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

    @Override
    public List<AutomationResponse> automations() {
        LocalDateTime from = LocalDateTime.now().minusDays(30);
        Map<String, AutomationRunRepository.JobSummary> jobs = new HashMap<>();
        runs.summarySince(from).forEach(j -> jobs.put(j.getJob(), j));
        Map<String, IntegrationEventRepository.NameSummary> emails = new HashMap<>();
        integrationEvents.byName(IntegrationKey.BREVO, from).forEach(n -> emails.put(n.getName(), n));
        return AutomationCatalog.ALL.stream().map(a -> view(a, jobs, emails)).toList();
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

    private static long nz(Long v) {
        return v == null ? 0 : v;
    }
}
