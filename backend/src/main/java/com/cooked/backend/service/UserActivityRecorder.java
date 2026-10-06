package com.cooked.backend.service;

import java.util.UUID;

/** Records that a user opened the app today (idempotent, never throws). */
public interface UserActivityRecorder {
    void recordToday(UUID userId);
}
