package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.UpdateWorkspaceSettingsRequest;
import com.cooked.backend.dto.response.WorkspaceSettingsResponse;
import com.cooked.backend.entity.WorkspaceSettings;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.repository.WorkspaceSettingsRepository;
import com.cooked.backend.service.WorkspaceSettingsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
@Transactional(readOnly = true)
public class WorkspaceSettingsServiceImpl implements WorkspaceSettingsService {

    static final String SLACK_PREFIX = "https://hooks.slack.com/";

    private final WorkspaceSettingsRepository repository;
    private final String supportEmail;

    public WorkspaceSettingsServiceImpl(WorkspaceSettingsRepository repository,
                                        @Value("${support.notification.email:contact@cookedapp.com}") String supportEmail) {
        this.repository = repository;
        this.supportEmail = supportEmail;
    }

    @Override
    public WorkspaceSettings current() {
        return repository.findById(WorkspaceSettings.SINGLETON_ID).orElseGet(WorkspaceSettings::defaults);
    }

    @Override
    public WorkspaceSettingsResponse get() {
        return toResponse(current());
    }

    @Override
    @Transactional
    public WorkspaceSettingsResponse update(UpdateWorkspaceSettingsRequest r) {
        WorkspaceSettings s = current();
        if (r.getWorkspaceName() != null) {
            if (r.getWorkspaceName().isBlank()) throw new BadRequestException("Workspace name can't be empty");
            s.setWorkspaceName(r.getWorkspaceName().trim());
        }
        if (r.getTimezone() != null) {
            try {
                s.setTimezone(ZoneId.of(r.getTimezone().trim()).getId());
            } catch (DateTimeException e) {
                throw new BadRequestException("Unknown timezone: " + r.getTimezone());
            }
        }
        if (r.getCriticalAlertsEmail() != null) s.setCriticalAlertsEmail(r.getCriticalAlertsEmail());
        if (r.getDailySummary() != null) s.setDailySummary(r.getDailySummary());
        if (r.getWeeklyReview() != null) s.setWeeklyReview(r.getWeeklyReview());
        if (r.getAnomalyCards() != null) s.setAnomalyCards(r.getAnomalyCards());
        if (r.getSlackWebhookUrl() != null) {
            String url = r.getSlackWebhookUrl().trim();
            if (!url.isEmpty() && !url.startsWith(SLACK_PREFIX)) throw new BadRequestException("Slack webhook must start with " + SLACK_PREFIX);
            s.setSlackWebhookUrl(url.isEmpty() ? null : url);
        }
        return toResponse(repository.save(s));
    }

    @Override
    @Transactional
    public void markDailySummarySent(LocalDate day) {
        WorkspaceSettings s = current();
        s.setLastDailySummaryOn(day);
        repository.save(s);
    }

    @Override
    @Transactional
    public void markWeeklyReviewSent(LocalDate day) {
        WorkspaceSettings s = current();
        s.setLastWeeklyReviewOn(day);
        repository.save(s);
    }

    WorkspaceSettingsResponse toResponse(WorkspaceSettings s) {
        String url = s.getSlackWebhookUrl();
        return WorkspaceSettingsResponse.builder()
                .workspaceName(s.getWorkspaceName()).timezone(s.getTimezone())
                .criticalAlertsEmail(s.isCriticalAlertsEmail()).dailySummary(s.isDailySummary()).weeklyReview(s.isWeeklyReview())
                .slackConfigured(url != null)
                .slackWebhookHint(url == null ? null : SLACK_PREFIX + "…/" + url.substring(Math.max(SLACK_PREFIX.length(), url.length() - 4)))
                .anomalyCards(s.isAnomalyCards())
                .lastDailySummaryOn(s.getLastDailySummaryOn()).lastWeeklyReviewOn(s.getLastWeeklyReviewOn())
                .supportEmail(supportEmail)
                .build();
    }
}
