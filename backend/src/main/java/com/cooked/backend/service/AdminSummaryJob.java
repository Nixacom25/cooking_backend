package com.cooked.backend.service;

import com.cooked.backend.entity.Role;
import com.cooked.backend.entity.WorkspaceSettings;
import com.cooked.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Checks every hour whether it is 8 AM in the workspace timezone and, if enabled
 * in Settings, sends the daily summary (and on Mondays the weekly review) to
 * every admin by email and to Slack. Each one is sent at most once a day.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSummaryJob {

    static final int SEND_HOUR = 8;

    private final WorkspaceSettingsService settings;
    private final AdminSummaryBuilder builder;
    private final EmailService emailService;
    private final SlackNotifier slack;
    private final UserRepository userRepository;
    private final Clock clock = Clock.systemUTC();

    @Scheduled(cron = "0 0 * * * ?")
    public void sendDueSummaries() {
        runAt(ZonedDateTime.now(clock));
    }

    void runAt(ZonedDateTime nowUtc) {
        WorkspaceSettings s = settings.current();
        ZonedDateTime local = nowUtc.withZoneSameInstant(zone(s.getTimezone()));
        if (local.getHour() != SEND_HOUR) return;
        if (s.isDailySummary() && !local.toLocalDate().equals(s.getLastDailySummaryOn())) {
            send(s, builder.daily(), "Daily summary");
            settings.markDailySummarySent(local.toLocalDate());
        }
        if (s.isWeeklyReview() && local.getDayOfWeek() == DayOfWeek.MONDAY && !local.toLocalDate().equals(s.getLastWeeklyReviewOn())) {
            send(s, builder.weekly(), "Weekly review");
            settings.markWeeklyReviewSent(local.toLocalDate());
        }
    }

    private void send(WorkspaceSettings s, AdminSummaryBuilder.Summary summary, String kind) {
        String subject = s.getWorkspaceName() + " · " + kind + " · " + summary.period();
        List<String> admins = userRepository.findEmailsByRole(Role.ADMIN);
        admins.forEach(to -> emailService.sendAdminSummaryEmail(to, subject, kind + " · " + summary.period(), summary.rows()));
        StringBuilder text = new StringBuilder("*" + subject + "*");
        summary.rows().forEach(r -> text.append("\n• ").append(r[0]).append(": ").append(r[1]));
        slack.post(text.toString());
        log.info("{} sent to {} admin(s)", kind, admins.size());
    }

    private static ZoneId zone(String id) {
        try {
            return ZoneId.of(id);
        } catch (RuntimeException e) {
            return ZoneId.of("UTC");
        }
    }
}
