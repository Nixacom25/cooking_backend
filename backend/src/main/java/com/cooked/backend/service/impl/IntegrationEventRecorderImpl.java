package com.cooked.backend.service.impl;

import com.cooked.backend.entity.IntegrationEvent;
import com.cooked.backend.entity.IntegrationKey;
import com.cooked.backend.repository.IntegrationEventRepository;
import com.cooked.backend.service.IntegrationEventRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Off the request thread and in its own transaction: logging must never slow down or break the caller. */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationEventRecorderImpl implements IntegrationEventRecorder {

    private final IntegrationEventRepository repository;

    @Async
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(IntegrationKey integration, String name, boolean success, Integer httpStatus, Integer latencyMs, String detail) {
        try {
            repository.save(IntegrationEvent.builder()
                    .integration(integration)
                    .name(shorten(name == null || name.isBlank() ? "unknown" : name, 80))
                    .success(success)
                    .httpStatus(httpStatus)
                    .latencyMs(latencyMs)
                    .detail(success ? null : shorten(detail, IntegrationEvent.TEXT_MAX))
                    .build());
        } catch (RuntimeException e) {
            log.warn("Integration event not recorded ({} {}): {}", integration, name, e.getMessage());
        }
    }

    static String shorten(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
