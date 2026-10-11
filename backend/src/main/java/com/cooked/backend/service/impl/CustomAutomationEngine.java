package com.cooked.backend.service.impl;

import com.cooked.backend.entity.*;
import com.cooked.backend.entity.CustomAutomation.Trigger;
import com.cooked.backend.repository.*;
import com.cooked.backend.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs the active custom automations: time triggers every hour, crash spikes
 * every 5 minutes, events (payment / import failed, creator applied) as they
 * happen. Each user is reached once per flow (once per 30 days for "inactive").
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAutomationEngine {

    static final int BATCH = 500;
    static final LocalDateTime EVER = LocalDateTime.of(2000, 1, 1, 0, 0);

    private final CustomAutomationRepository automations;
    private final CustomAutomationFireRepository fires;
    private final UserRepository users;
    private final SubscriptionPaymentRepository payments;
    private final EmailCampaignSendRepository campaignSends;
    private final PushNotificationService push;
    private final EmailService email;
    private final EmailCampaignService campaigns;
    private final SlackNotifier slack;
    private final WorkspaceSettingsService settings;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // --- time triggers -------------------------------------------------------------------------

    @Scheduled(cron = "0 5 * * * *")
    public void runHourly() {
        if (!running.compareAndSet(false, true)) return;
        try {
            ZonedDateTime local = ZonedDateTime.now(zone());
            for (CustomAutomation a : automations.findAllByStatus(CustomAutomation.Status.ACTIVE)) {
                try {
                    switch (a.getTrigger()) {
                        case TRIAL_ENDS_IN, SIGNED_UP_DAYS_AGO, INACTIVE_DAYS -> runUsers(a);
                        case WEEKLY_MONDAY -> runWeekly(a, local);
                        default -> { }
                    }
                    if (a.getTrigger().aboutUser() && local.getHour() == 9) dailyDigest(a);
                } catch (Exception e) {
                    log.error("Automation {} failed: {}", a.getId(), e.getMessage());
                }
            }
        } finally {
            running.set(false);
        }
    }

    void runUsers(CustomAutomation a) {
        LocalDateTime now = LocalDateTime.now();
        int n = a.getTriggerValue() == null ? 1 : a.getTriggerValue();
        LocalDateTime from, until, firedSince;
        switch (a.getTrigger()) {
            case TRIAL_ENDS_IN -> { from = now; until = now.plusDays(n); firedSince = EVER; }
            case SIGNED_UP_DAYS_AGO -> { from = now.minusDays(n + 2L); until = now.minusDays(n); firedSince = EVER; }
            default -> { from = now.minusDays(n + 7L); until = now.minusDays(n); firedSince = now.minusDays(30); } // INACTIVE_DAYS
        }
        List<User> batch = automations.candidates(a.getTrigger().name(), from, until, a.getAudience().name(), a.getOpened().name(),
                now.minusDays(7), a.getId(), firedSince, PageRequest.of(0, BATCH));
        for (User u : batch) act(a, u);
        a.setLastRunAt(now);
        automations.save(a);
        if (batch.size() == BATCH) log.info("Automation {}: {} users this hour, more next hour", a.getName(), BATCH);
    }

    void runWeekly(CustomAutomation a, ZonedDateTime local) {
        int hour = a.getTriggerValue() == null ? 9 : a.getTriggerValue();
        if (local.getDayOfWeek() != DayOfWeek.MONDAY || local.getHour() != hour) return;
        if (fires.existsByAutomationIdAndUserIdIsNullAndFiredAtAfter(a.getId(), LocalDateTime.now().minusDays(6))) return;
        LocalDateTime week = LocalDateTime.now().minusDays(7);
        BigDecimal revenue = payments.sumSuccessBetween(week, LocalDateTime.now());
        long signups = users.count() == 0 ? 0 : users.countByCreatedAtAfter(week);
        String text = (a.getSlackText() == null ? a.getName() : a.getSlackText())
                + "\nLast 7 days · new users: " + signups + " · revenue: $" + (revenue == null ? "0" : revenue.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        team(a, text, List.<String[]>of(new String[]{"New users (7 days)", String.valueOf(signups)},
                new String[]{"Revenue (7 days)", "$" + (revenue == null ? "0" : revenue.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString())}));
        record(a, null, false, false);
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
    public void checkErrorSpikes() {
        for (CustomAutomation a : automations.findAllByStatusAndTrigger(CustomAutomation.Status.ACTIVE, Trigger.ERRORS_SPIKE)) {
            int threshold = a.getTriggerValue() == null ? 5 : a.getTriggerValue();
            List<LocalDateTime> recent = automations.criticalErrorTimesSince(LocalDateTime.now().minusMinutes(15));
            if (recent.size() < threshold) continue;
            if (fires.existsByAutomationIdAndUserIdIsNullAndFiredAtAfter(a.getId(), LocalDateTime.now().minusHours(1))) continue;
            String text = (a.getSlackText() == null ? a.getName() : a.getSlackText()) + ": " + recent.size() + " crashes reported in the last 15 minutes. See Errors & Crashes.";
            team(a, text, List.<String[]>of(new String[]{"Crashes in 15 minutes", String.valueOf(recent.size())}));
            record(a, null, false, false);
        }
    }

    // --- events -------------------------------------------------------------------------------------

    @Async
    @EventListener
    public void onEvent(AutomationTriggerEvent e) {
        Trigger t;
        try {
            t = Trigger.valueOf(e.trigger());
        } catch (IllegalArgumentException ex) {
            return;
        }
        for (CustomAutomation a : automations.findAllByStatusAndTrigger(CustomAutomation.Status.ACTIVE, t)) {
            try {
                if (t.aboutUser()) {
                    User u = e.userId() != null ? users.findById(e.userId()).orElse(null) : e.email() == null ? null : users.findByEmail(e.email()).orElse(null);
                    if (u == null) continue;
                    // Same user: at most once a day for imports, once a week for payments.
                    LocalDateTime since = t == Trigger.IMPORT_FAILED ? LocalDateTime.now().minusDays(1) : LocalDateTime.now().minusDays(7);
                    if (automations.userPasses(u.getId(), a.getAudience().name(), a.getOpened().name(), LocalDateTime.now().minusDays(7), a.getId(), since) == 0) continue;
                    act(a, u);
                    if (a.isSlackEnabled()) slack.post((a.getSlackText() == null ? a.getName() : a.getSlackText()) + (e.detail() == null ? "" : " · " + e.detail()));
                } else {
                    String text = (a.getSlackText() == null ? a.getName() : a.getSlackText()) + (e.detail() == null ? "" : ": " + e.detail());
                    team(a, text, List.<String[]>of(new String[]{"Event", a.getName()}, new String[]{"Details", e.detail() == null ? "—" : e.detail()}));
                    record(a, null, false, false);
                }
            } catch (Exception ex) {
                log.error("Automation {} failed on event {}: {}", a.getId(), t, ex.getMessage());
            }
        }
    }

    // --- actions --------------------------------------------------------------------------------------

    void act(CustomAutomation a, User u) {
        boolean pushed = false;
        boolean emailed = false;
        if (a.isPushEnabled() && u.getFcmToken() != null && !u.getFcmToken().isBlank() && u.isPushEnabled() && u.isPushRemindersEnabled()) {
            try {
                push.sendPush(u.getFcmToken(), personalise(a.getPushTitle(), u), personalise(a.getPushBody(), u),
                        Map.of("type", "automation", "automationId", a.getId().toString()));
                pushed = true;
            } catch (Exception e) {
                log.warn("Automation push failed for {}: {}", u.getId(), e.getMessage());
            }
        }
        if (a.isEmailEnabled() && u.getEmail() != null && u.isEmailMarketingEnabled()
                && campaignSends.countByUserIdAndSentAtAfter(u.getId(), LocalDateTime.now().minusDays(7)) < 2) {
            EmailCampaign c = EmailCampaign.builder().title(a.getEmailTitle()).body(a.getEmailBody()).buttonLabel(a.getEmailButton())
                    .buttonUrl(a.getEmailButtonUrl()).status(EmailCampaign.Status.SENDING).build();
            String unsub = campaigns.unsubscribeLink(u.getId());
            if (email.sendCampaignEmail(u.getEmail(), personalise(a.getEmailSubject(), u), EmailCampaignServiceImpl.html(c, u.getFirstname(), unsub), unsub)) {
                // Counts toward the shared 2-emails-per-week cap.
                campaignSends.save(EmailCampaignSend.builder().campaignId(UUID.randomUUID()).userId(u.getId()).sentAt(LocalDateTime.now()).build());
                emailed = true;
            }
        }
        record(a, u.getId(), pushed, emailed);
    }

    /** Yesterday's count for a user flow, to Slack and/or the team's inbox (once a day at 9 AM). */
    void dailyDigest(CustomAutomation a) {
        if (!a.isSlackEnabled() && !a.isTeamEmailEnabled()) return;
        if (fires.existsByAutomationIdAndUserIdIsNullAndFiredAtAfter(a.getId(), LocalDateTime.now().minusHours(20))) return;
        long n = fires.countByAutomationIdAndUserIdIsNotNullAndFiredAtAfter(a.getId(), LocalDateTime.now().minusDays(1));
        if (n == 0) return;
        String text = (a.getSlackText() == null ? a.getName() : a.getSlackText()) + ": reached " + n + " user" + (n == 1 ? "" : "s") + " in the last 24 hours.";
        team(a, text, List.<String[]>of(new String[]{"Users reached (24 h)", String.valueOf(n)}));
        record(a, null, false, false);
    }

    private void team(CustomAutomation a, String text, List<String[]> rows) {
        if (a.isSlackEnabled()) slack.post(text);
        if (a.isTeamEmailEnabled()) {
            for (User admin : users.findAllByRole(Role.ADMIN)) {
                try {
                    email.sendAdminSummaryEmail(admin.getEmail(), "Cooked · " + a.getName(), a.getName(), rows);
                } catch (Exception e) {
                    log.warn("Automation team email failed: {}", e.getMessage());
                }
            }
        }
    }

    private void record(CustomAutomation a, UUID userId, boolean pushed, boolean emailed) {
        fires.save(CustomAutomationFire.builder().automationId(a.getId()).userId(userId).firedAt(LocalDateTime.now()).pushSent(pushed).emailSent(emailed).build());
    }

    static String personalise(String s, User u) {
        String name = u.getFirstname() == null || u.getFirstname().isBlank() ? "there" : u.getFirstname().trim();
        return s == null ? "" : s.replace("{firstname}", name);
    }

    private ZoneId zone() {
        try {
            return ZoneId.of(settings.current().getTimezone());
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }
}
