package com.cooked.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;

/**
 * Initializes the Firebase Admin SDK for server-side push notifications
 * (FCM). This must point at the SAME Firebase project the mobile app is
 * registered with (see mobile/android/app/google-services.json /
 * mobile/ios/Runner/GoogleService-Info.plist) - a token registered under one
 * project cannot be messaged using another project's credentials.
 *
 * Configure with either:
 *  - fcm.credentials.base64: the service account JSON, base64-encoded
 *    (Project Settings > Service Accounts > Generate new private key, in the
 *    Firebase console, for the SAME project as the mobile app), or
 *  - a classpath resource named "firebase-fcm-service-account.json" in
 *    src/main/resources.
 *
 * If neither is configured, push sending is disabled and
 * PushNotificationService no-ops with a warning instead of crashing the app.
 */
@Slf4j
@Component
public class FirebaseConfig {

    @Value("${fcm.credentials.base64:}")
    private String credentialsBase64;

    private boolean initialized = false;

    @PostConstruct
    public void init() {
        try {
            InputStream credentialsStream = resolveCredentialsStream();
            if (credentialsStream == null) {
                log.warn("Firebase Cloud Messaging is not configured (no fcm.credentials.base64 and no "
                        + "firebase-fcm-service-account.json on the classpath). Push notifications will be skipped.");
                return;
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentialsStream))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
            initialized = true;
            log.info("Firebase Admin SDK initialized for push notifications.");
        } catch (Exception e) {
            log.error("Failed to initialize Firebase Admin SDK - push notifications will be skipped: {}", e.getMessage(), e);
        }
    }

    private InputStream resolveCredentialsStream() throws Exception {
        if (credentialsBase64 != null && !credentialsBase64.trim().isEmpty()) {
            byte[] decoded = Base64.getDecoder().decode(credentialsBase64.trim());
            return new ByteArrayInputStream(decoded);
        }

        org.springframework.core.io.ClassPathResource resource =
                new org.springframework.core.io.ClassPathResource("firebase-fcm-service-account.json");
        return resource.exists() ? resource.getInputStream() : null;
    }

    public boolean isInitialized() {
        return initialized;
    }
}
