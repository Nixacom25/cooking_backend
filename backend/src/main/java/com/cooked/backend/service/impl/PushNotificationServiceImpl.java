package com.cooked.backend.service.impl;

import com.cooked.backend.config.FirebaseConfig;
import com.cooked.backend.service.PushNotificationService;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationServiceImpl implements PushNotificationService {

    private final FirebaseConfig firebaseConfig;

    @Override
    @Async
    public void sendPush(String fcmToken, String title, String body, Map<String, String> data) {
        if (!firebaseConfig.isInitialized()) {
            log.warn("Skipping push notification '{}' - Firebase Cloud Messaging is not configured.", title);
            return;
        }
        if (fcmToken == null || fcmToken.isBlank()) {
            log.debug("Skipping push notification '{}' - recipient has no FCM token registered.", title);
            return;
        }

        Message message = Message.builder()
                .setToken(fcmToken)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putAllData(data != null ? data : Collections.emptyMap())
                .setAndroidConfig(AndroidConfig.builder()
                        .setNotification(AndroidNotification.builder()
                                .setSound("default")
                                .build())
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .setAps(Aps.builder()
                                .setSound("default")
                                .build())
                        .build())
                .build();

        try {
            String messageId = FirebaseMessaging.getInstance().send(message);
            log.info("Sent push notification '{}' (messageId={})", title, messageId);
        } catch (FirebaseMessagingException e) {
            log.error("Failed to send push notification '{}': {}", title, e.getMessage());
        }
    }
}
