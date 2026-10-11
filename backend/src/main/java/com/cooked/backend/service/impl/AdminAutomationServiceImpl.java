package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.AutomationResponse;
import com.cooked.backend.entity.AutomationRun;
import com.cooked.backend.entity.IntegrationKey;
import com.cooked.backend.repository.AutomationRunRepository;
import com.cooked.backend.repository.IntegrationEventRepository;
import com.cooked.backend.service.AdminAutomationService;
import com.cooked.backend.service.automation.AutomationCatalog;
import com.cooked.backend.service.automation.AutomationCatalog.Automation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAutomationServiceImpl implements AdminAutomationService {

    private final AutomationRunRepository runs;
    private final IntegrationEventRepository integrationEvents;
    private final com.cooked.backend.service.CustomAutomationService customAutomations;

    @Override
    public List<AutomationResponse> automations() {
        LocalDateTime from = LocalDateTime.now().minusDays(30);
        Map<String, AutomationRunRepository.JobSummary> jobs = new HashMap<>();
        runs.summarySince(from).forEach(j -> jobs.put(j.getJob(), j));
        Map<String, IntegrationEventRepository.NameSummary> emails = new HashMap<>();
        integrationEvents.byName(IntegrationKey.BREVO, from).forEach(n -> emails.put(n.getName(), n));
        List<AutomationResponse> out = new java.util.ArrayList<>(AutomationCatalog.ALL.stream().map(a -> view(a, jobs, emails)).toList());
        // Flows built in the backoffice (New automation), after the built-in ones.
        customAutomations.list().forEach(c -> out.add(AutomationResponse.builder()
                .key("custom:" + c.id()).name(c.name()).kind("CUSTOM").trigger(c.trigger() + (c.triggerValue() == null ? "" : " = " + c.triggerValue()))
                .actions(customActions(c)).lastRunAt(c.lastRunAt()).runs30d(c.fired30d()).failures30d(0)
                .note("ACTIVE".equals(c.status()) ? null : c.status().charAt(0) + c.status().substring(1).toLowerCase() + " · built in the backoffice")
                .build()));
        return out;
    }

    private AutomationResponse view(Automation a, Map<String, AutomationRunRepository.JobSummary> jobs,
                                    Map<String, IntegrationEventRepository.NameSummary> emails) {
        AutomationResponse.AutomationResponseBuilder b = AutomationResponse.builder()
                .key(a.key()).name(a.name()).kind(a.kind().name()).trigger(a.trigger()).actions(a.actions()).note(a.note());
        if (a.job() != null) {
            AutomationRunRepository.JobSummary s = jobs.get(a.job());
            if (s != null) {
                b.runs30d(nz(s.getTotal())).failures30d(nz(s.getFailures())).avgDurationMs(s.getAvgDurationMs()).lastRunAt(s.getLastAt());
            }
            AutomationRun last = runs.findFirstByJobOrderByCreatedAtDesc(a.job());
            if (last != null) b.lastRunAt(last.getCreatedAt()).lastRunSuccess(last.isSuccess()).lastError(last.getDetail());
        } else if (a.email() != null) {
            IntegrationEventRepository.NameSummary n = emails.get(a.email().name());
            if (n != null) {
                b.runs30d(nz(n.getTotal())).failures30d(nz(n.getFailures())).lastRunAt(n.getLastAt());
            }
        }
        return b.build();
    }

    private static List<String> customActions(com.cooked.backend.dto.response.CustomAutomationResponse c) {
        List<String> a = new java.util.ArrayList<>();
        if (c.pushEnabled()) a.add("Push: " + c.pushTitle());
        if (c.emailEnabled()) a.add("Email: " + c.emailSubject());
        if (c.slackEnabled()) a.add("Slack" + (c.slackText() == null ? "" : ": " + c.slackText()));
        if (c.teamEmailEnabled()) a.add("Email the admin team");
        return a;
    }

    private static long nz(Long v) {
        return v == null ? 0 : v;
    }
}
