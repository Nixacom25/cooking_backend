package com.cooked.backend.service.impl;

import com.cooked.backend.entity.AutomationRun;
import com.cooked.backend.repository.AutomationRunRepository;
import com.cooked.backend.service.AutomationRunRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationRunRecorderImpl implements AutomationRunRecorder {

    private final AutomationRunRepository repository;

    @Async
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String job, boolean success, int durationMs, String detail) {
        try {
            repository.save(AutomationRun.builder().job(IntegrationEventRecorderImpl.shorten(job, 120)).success(success)
                    .durationMs(durationMs).detail(success ? null : IntegrationEventRecorderImpl.shorten(detail, 160)).build());
        } catch (RuntimeException e) {
            log.warn("Automation run not recorded ({}): {}", job, e.getMessage());
        }
    }
}
