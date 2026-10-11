package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.CustomAutomationRequest;
import com.cooked.backend.dto.response.AutomationTemplateResponse;
import com.cooked.backend.dto.response.CustomAutomationResponse;
import com.cooked.backend.entity.CustomAutomation;
import com.cooked.backend.entity.CustomAutomation.Trigger;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.CustomAutomationFireRepository;
import com.cooked.backend.repository.CustomAutomationRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.ActivityLogService;
import com.cooked.backend.service.CustomAutomationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomAutomationServiceImpl implements CustomAutomationService {

    private final CustomAutomationRepository automations;
    private final CustomAutomationFireRepository fires;
    private final UserRepository users;
    private final ActivityLogService activityLog;

    // --- templates ----------------------------------------------------------------------------

    private static CustomAutomationRequest preset(String name, String key, String trigger, Integer value, String audience, String opened,
                                                  boolean push, String pushTitle, String pushBody,
                                                  boolean email, String subject, String title, String body, String button,
                                                  boolean slack, String slackText, boolean team) {
        return new CustomAutomationRequest(name, key, trigger, value, audience, opened, push, pushTitle, pushBody,
                email, subject, title, body, button, email ? "https://cookedapp.com" : null, slack, slackText, team);
    }

    static final List<AutomationTemplateResponse> TEMPLATES = List.of(
            new AutomationTemplateResponse("trial_ending", "Trial ending", "Revenue", "Nudges trial users 1 day before the trial ends.",
                    "trial_ends_in = 1d", List.of("Push", "Email"), true, true, null,
                    preset("Trial ending · nudge", "trial_ending", "TRIAL_ENDS_IN", 1, "TRIAL", "ANY",
                            true, "Your trial ends tomorrow 🍳", "Keep Scan and your cookbooks — tap to stay Premium.",
                            true, "Your trial ends tomorrow", "Your trial ends tomorrow, {firstname}",
                            "Keep scanning your fridge and getting recipes you can cook tonight.\n\nStay on Premium and keep your cookbooks.", "Keep Premium",
                            true, "Trial ending · nudge", false)),
            new AutomationTemplateResponse("payment_failed", "Payment failed", "Revenue",
                    "Pushes the user to update their billing and alerts the team. The recovery email already goes out automatically.",
                    "payment_failed", List.of("Push", "Slack"), false, true, null,
                    preset("Payment failed · follow-up", "payment_failed", "PAYMENT_FAILED", null, "ANY", "ANY",
                            true, "Your payment didn’t go through", "Update your billing details to keep Premium.",
                            false, null, null, null, null, true, "Payment failed", false)),
            new AutomationTemplateResponse("inactive_users", "Inactive users", "Engagement",
                    "Brings back people who haven’t opened Cooked in 14 days.", "last_open > 14d", List.of("Push", "Email"), false, true, null,
                    preset("Inactive 14 days · come back", "inactive_users", "INACTIVE_DAYS", 14, "ANY", "ANY",
                            true, "Your fridge misses you 🥕", "Scan what you have and get a recipe for tonight.",
                            true, "Something to cook tonight?", "Hi {firstname}, what’s in your fridge?",
                            "Scan your fridge and Cooked suggests recipes you can make right now.", "Open Cooked",
                            false, null, false)),
            new AutomationTemplateResponse("weekly_summary", "Weekly summary", "Operations", "Posts key figures to Slack every Monday at 9 AM.",
                    "Every Monday 9:00", List.of("Slack"), false, true, null,
                    preset("Weekly summary", "weekly_summary", "WEEKLY_MONDAY", 9, "ANY", "ANY",
                            false, null, null, false, null, null, null, null, true, "Cooked · weekly summary", false)),
            new AutomationTemplateResponse("first_recipe_cooked", "First recipe cooked", "Engagement",
                    "Celebrates the first “I cooked it” and asks for a rating.", "recipe_cooked = 1", List.of("Push"), false, false,
                    "Cooked doesn’t record when someone cooks a recipe yet.", null),
            new AutomationTemplateResponse("crash_spike", "Crash spike", "Operations",
                    "Alerts the team when 5 or more crashes are reported within 15 minutes.", "crashes ≥ 5 in 15 min", List.of("Slack", "Email"), false, true, null,
                    preset("Crash spike", "crash_spike", "ERRORS_SPIKE", 5, "ANY", "ANY",
                            false, null, null, false, null, null, null, null, true, "Crash spike in the Cooked app", true)),
            new AutomationTemplateResponse("import_failed", "Import failed", "Engagement",
                    "Suggests another way to save the recipe when an import fails.", "import_failed", List.of("Push"), false, true, null,
                    preset("Import failed · help", "import_failed", "IMPORT_FAILED", null, "ANY", "ANY",
                            true, "That link didn’t import", "Try the recipe page itself, or add the recipe by hand in Cooked.",
                            false, null, null, null, null, false, null, false)),
            new AutomationTemplateResponse("creator_applied", "Creator applied", "Team",
                    "Notifies the team when someone applies to the creator or ambassador program.", "creator_applied", List.of("Slack", "Email"), false, true, null,
                    preset("Creator applied", "creator_applied", "CREATOR_APPLIED", null, "ANY", "ANY",
                            false, null, null, false, null, null, null, null, true, "New creator application", true))
    );

    @Override
    public List<AutomationTemplateResponse> templates() {
        return TEMPLATES;
    }

    // --- CRUD -----------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<CustomAutomationResponse> list() {
        return automations.findAllByOrderByCreatedAtDesc().stream().map(this::view).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomAutomationResponse get(UUID id) {
        return view(find(id));
    }

    @Override
    @Transactional
    public CustomAutomationResponse create(CustomAutomationRequest r, String adminEmail) {
        CustomAutomation a = CustomAutomation.builder().status(CustomAutomation.Status.DRAFT).createdBy(adminEmail)
                .audience(CustomAutomation.Audience.ANY).opened(CustomAutomation.Opened.ANY).trigger(Trigger.valueOf(r.trigger())).name(r.name().trim()).build();
        apply(a, r);
        a = automations.save(a);
        log(adminEmail, "Automation created", a);
        return view(a);
    }

    @Override
    @Transactional
    public CustomAutomationResponse update(UUID id, CustomAutomationRequest r, String adminEmail) {
        CustomAutomation a = find(id);
        apply(a, r);
        if (a.getStatus() == CustomAutomation.Status.ACTIVE) a.setStatus(CustomAutomation.Status.PAUSED);
        a.setDryRunAt(null); // a change needs a new dry run
        log(adminEmail, "Automation changed", a);
        return view(automations.save(a));
    }

    static void apply(CustomAutomation a, CustomAutomationRequest r) {
        Trigger t = Trigger.valueOf(r.trigger());
        a.setName(r.name().trim());
        a.setTemplateKey(r.templateKey());
        a.setTrigger(t);
        a.setTriggerValue(value(t, r.triggerValue()));
        a.setAudience(r.audience() == null ? CustomAutomation.Audience.ANY : CustomAutomation.Audience.valueOf(r.audience()));
        a.setOpened(r.opened() == null ? CustomAutomation.Opened.ANY : CustomAutomation.Opened.valueOf(r.opened()));
        if (!t.aboutUser() && (r.pushEnabled() || r.emailEnabled())) {
            throw new BadRequestException("This trigger isn’t about one user: use Slack or the team email.");
        }
        a.setPushEnabled(r.pushEnabled());
        a.setPushTitle(trim(r.pushTitle()));
        a.setPushBody(trim(r.pushBody()));
        a.setEmailEnabled(r.emailEnabled());
        a.setEmailSubject(trim(r.emailSubject()));
        a.setEmailTitle(trim(r.emailTitle()));
        a.setEmailBody(trim(r.emailBody()));
        a.setEmailButton(trim(r.emailButton()));
        a.setEmailButtonUrl(trim(r.emailButtonUrl()));
        a.setSlackEnabled(r.slackEnabled());
        a.setSlackText(trim(r.slackText()));
        a.setTeamEmailEnabled(r.teamEmailEnabled());
    }

    /** Value of the trigger within its allowed range (default when missing). */
    static Integer value(Trigger t, Integer v) {
        int[] range = switch (t) {
            case TRIAL_ENDS_IN -> new int[]{1, 14, 1};
            case SIGNED_UP_DAYS_AGO -> new int[]{1, 60, 3};
            case INACTIVE_DAYS -> new int[]{3, 90, 14};
            case WEEKLY_MONDAY -> new int[]{0, 23, 9};
            case ERRORS_SPIKE -> new int[]{2, 100, 5};
            default -> null;
        };
        if (range == null) return null;
        int x = v == null ? range[2] : v;
        if (x < range[0] || x > range[1]) throw new BadRequestException("The trigger value must be between " + range[0] + " and " + range[1] + ".");
        return x;
    }

    static List<String> problems(CustomAutomation a) {
        List<String> p = new ArrayList<>();
        if (!a.isPushEnabled() && !a.isEmailEnabled() && !a.isSlackEnabled() && !a.isTeamEmailEnabled()) p.add("choose at least one action");
        if (a.isPushEnabled() && (blank(a.getPushTitle()) || blank(a.getPushBody()))) p.add("write the push title and text");
        if (a.isEmailEnabled() && (blank(a.getEmailSubject()) || blank(a.getEmailTitle()) || blank(a.getEmailBody()))) p.add("write the email subject, title and text");
        return p;
    }

    // --- dry run & activation -----------------------------------------------------------------

    @Override
    @Transactional
    public CustomAutomationResponse dryRun(UUID id) {
        CustomAutomation a = find(id);
        List<String> p = problems(a);
        if (!p.isEmpty()) throw new BadRequestException("Before the dry run: " + String.join(", ", p) + ".");
        LocalDateTime now = LocalDateTime.now();
        int triggered;
        int passed;
        int push = 0;
        int email = 0;
        if (a.getTrigger().aboutUser() && a.getTrigger() != Trigger.PAYMENT_FAILED && a.getTrigger() != Trigger.IMPORT_FAILED) {
            LocalDateTime[] w = dryWindow(a.getTrigger(), a.getTriggerValue(), now);
            String trig = a.getTrigger().name(), aud = a.getAudience().name(), op = a.getOpened().name();
            triggered = (int) automations.countWindow(trig, w[0], w[1]);
            passed = (int) automations.countPassing(trig, w[0], w[1], aud, op, now.minusDays(7));
            if (a.isPushEnabled()) push = (int) automations.countPushable(trig, w[0], w[1], aud, op, now.minusDays(7));
            if (a.isEmailEnabled()) email = (int) automations.countEmailable(trig, w[0], w[1], aud, op, now.minusDays(7), now.minusDays(7));
        } else {
            LocalDateTime week = now.minusDays(7);
            triggered = switch (a.getTrigger()) {
                case PAYMENT_FAILED -> (int) automations.paymentFailureEmailsSince(week);
                case IMPORT_FAILED -> (int) automations.importFailuresSince(week);
                case CREATOR_APPLIED -> (int) automations.creatorApplicationsSince(week);
                case ERRORS_SPIKE -> spikes(automations.criticalErrorTimesSince(week), a.getTriggerValue());
                default -> 1; // WEEKLY_MONDAY
            };
            passed = triggered; // conditions are checked on each event when it happens
            if (a.isPushEnabled()) push = triggered;
            if (a.isEmailEnabled()) email = triggered;
        }
        a.setDryRunAt(now);
        a.setDryTriggered(triggered);
        a.setDryPassed(passed);
        a.setDryPush(push);
        a.setDryEmail(email);
        return view(automations.save(a));
    }

    /** The real-data window a dry run looks at. */
    static LocalDateTime[] dryWindow(Trigger t, Integer days, LocalDateTime now) {
        int n = days == null ? 1 : days;
        return switch (t) {
            case TRIAL_ENDS_IN -> new LocalDateTime[]{now, now.plusDays(n + 7L)};                    // trials ending soon
            default -> new LocalDateTime[]{now.minusDays(n + 7L), now.minusDays(n)};                 // crossed the threshold in the last 7 days
        };
    }

    static String dryLabel(Trigger t) {
        return switch (t) {
            case TRIAL_ENDS_IN -> "Trials ending in the next 7 days. Nothing is sent.";
            case WEEKLY_MONDAY -> "Runs once a week. Nothing is sent.";
            case PAYMENT_FAILED, IMPORT_FAILED, CREATOR_APPLIED, ERRORS_SPIKE -> "Events of the last 7 days. Nothing is sent.";
            default -> "Simulates the last 7 days. Nothing is sent.";
        };
    }

    /** Separate 15-minute windows with at least [threshold] errors. */
    static int spikes(List<LocalDateTime> times, Integer threshold) {
        int t = threshold == null ? 5 : threshold;
        int count = 0;
        int i = 0;
        while (i < times.size()) {
            int j = i;
            while (j < times.size() && Duration.between(times.get(i), times.get(j)).toMinutes() < 15) j++;
            if (j - i >= t) { count++; i = j; } else i++;
        }
        return count;
    }

    @Override
    @Transactional
    public CustomAutomationResponse activate(UUID id, String adminEmail) {
        CustomAutomation a = find(id);
        List<String> p = problems(a);
        if (!p.isEmpty()) throw new BadRequestException("Before activating: " + String.join(", ", p) + ".");
        if (!dryRunCurrent(a)) throw new BadRequestException("Run a dry run first (again after any change).");
        a.setStatus(CustomAutomation.Status.ACTIVE);
        log(adminEmail, "Automation activated", a);
        return view(automations.save(a));
    }

    @Override
    @Transactional
    public CustomAutomationResponse pause(UUID id, String adminEmail) {
        CustomAutomation a = find(id);
        a.setStatus(CustomAutomation.Status.PAUSED);
        log(adminEmail, "Automation paused", a);
        return view(automations.save(a));
    }

    @Override
    @Transactional
    public void delete(UUID id, String adminEmail) {
        CustomAutomation a = find(id);
        automations.delete(a);
        log(adminEmail, "Automation deleted", a);
    }

    static boolean dryRunCurrent(CustomAutomation a) {
        return a.getDryRunAt() != null;
    }

    private void log(String adminEmail, String title, CustomAutomation a) {
        users.findByEmail(adminEmail).ifPresent(u -> activityLog.logActivity(u, title, a.getName() + " · " + a.getTrigger(), a.getId(), "AUTOMATION"));
    }

    private CustomAutomation find(UUID id) {
        return automations.findById(id).orElseThrow(() -> new ResourceNotFoundException("Automation not found"));
    }

    CustomAutomationResponse view(CustomAutomation a) {
        CustomAutomationResponse.DryRun dry = a.getDryRunAt() == null ? null : new CustomAutomationResponse.DryRun(a.getDryRunAt(),
                a.getDryTriggered(), a.getDryPassed(), a.getDryPush(), a.getDryEmail(), dryLabel(a.getTrigger()));
        long fired = a.getId() == null ? 0 : fires.countByAutomationIdAndFiredAtAfter(a.getId(), LocalDateTime.now().minusDays(30));
        return new CustomAutomationResponse(a.getId(), a.getName(), a.getTemplateKey(), a.getTrigger().name(), a.getTriggerValue(),
                a.getAudience().name(), a.getOpened().name(), a.isPushEnabled(), a.getPushTitle(), a.getPushBody(),
                a.isEmailEnabled(), a.getEmailSubject(), a.getEmailTitle(), a.getEmailBody(), a.getEmailButton(), a.getEmailButtonUrl(),
                a.isSlackEnabled(), a.getSlackText(), a.isTeamEmailEnabled(), a.getStatus().name(), dry, dryRunCurrent(a), a.getLastRunAt(), fired,
                a.getCreatedBy(), a.getCreatedAt());
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
