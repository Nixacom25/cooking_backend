package com.cooked.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Refreshes provider spend every night (billing APIs settle the previous day late). */
@Component
@RequiredArgsConstructor
public class ProviderBillingSyncJob {

    private final AdminCostService costService;

    @Scheduled(cron = "0 30 4 * * ?", zone = "UTC")
    public void syncNightly() {
        costService.syncProviders();
    }
}
