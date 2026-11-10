package com.cooked.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every night: accounts whose deletion was requested more than 30 days ago are deleted for good. */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountDeletionJob {

    private final UserService userService;

    @Scheduled(cron = "0 30 2 * * ?")
    public void purge() {
        try {
            int n = userService.purgeAccountsPastGracePeriod();
            if (n > 0) log.info("[AccountDeletion] {} account(s) deleted for good after the 30-day grace period", n);
        } catch (RuntimeException e) {
            log.error("[AccountDeletion] purge failed", e);
        }
    }
}
