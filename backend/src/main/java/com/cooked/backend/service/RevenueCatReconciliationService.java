package com.cooked.backend.service;

import com.cooked.backend.entity.User;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Keeps recorded payments in line with RevenueCat when a webhook couldn't be
 * matched to an account (purchase made before sign-up, $RCAnonymousID).
 */
public interface RevenueCatReconciliationService {

    /** Keeps a webhook event whose app user id matches no account yet. */
    void savePending(Map<String, Object> event);

    /** Applies the pending events of this user's ids (our id and RevenueCat id); returns how many. */
    int resolveFor(User user);

    /** Starts reading every possible subscriber from RevenueCat and adding the missing real payments (background). */
    SyncStatus startSync(String adminEmail);

    SyncStatus status();

    record SyncStatus(boolean running, LocalDateTime startedAt, LocalDateTime finishedAt, int usersTotal, int usersChecked,
                      int paymentsAdded, int pendingResolved, int errors, String lastError, long pendingOpen, boolean configured) {
    }
}
