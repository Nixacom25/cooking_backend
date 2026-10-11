package com.cooked.backend.service.impl;

import com.cooked.backend.service.DataExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Export links last 7 days: their files are deleted the night after. */
@Component
@RequiredArgsConstructor
public class DataExportCleanupJob {

    private final DataExportService exports;

    @Scheduled(cron = "0 30 3 * * ?")
    public void expireOldExports() {
        exports.expireOldExports();
    }
}
