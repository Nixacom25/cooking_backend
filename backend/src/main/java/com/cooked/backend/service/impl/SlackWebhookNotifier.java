package com.cooked.backend.service.impl;

import com.cooked.backend.entity.IntegrationKey;
import com.cooked.backend.service.IntegrationEventRecorder;
import com.cooked.backend.service.SlackNotifier;
import com.cooked.backend.service.WorkspaceSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlackWebhookNotifier implements SlackNotifier {

    private final WorkspaceSettingsService settings;
    private final RestTemplate restTemplate;
    private final IntegrationEventRecorder integrationEvents;

    @Async
    @Override
    public void post(String text) {
        String url = settings.current().getSlackWebhookUrl();
        if (url == null || url.isBlank()) return;
        long start = System.nanoTime();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForEntity(url, new HttpEntity<>(Map.of("text", text), headers), String.class);
            integrationEvents.record(IntegrationKey.SLACK, "message", true, 200, elapsed(start), null);
        } catch (RuntimeException e) {
            log.warn("Slack post failed: {}", e.getMessage());
            integrationEvents.record(IntegrationKey.SLACK, "message", false, null, elapsed(start), e.getClass().getSimpleName());
        }
    }

    private static int elapsed(long start) {
        return (int) ((System.nanoTime() - start) / 1_000_000);
    }
}
