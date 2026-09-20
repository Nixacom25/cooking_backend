package com.cooked.backend.service;

import java.util.Map;

public interface PushNotificationService {

    /**
     * Sends a push notification to a single device. No-ops (with a log
     * warning) if Firebase isn't configured or fcmToken is blank, so callers
     * never need to guard against push being unavailable.
     */
    void sendPush(String fcmToken, String title, String body, Map<String, String> data);
}
