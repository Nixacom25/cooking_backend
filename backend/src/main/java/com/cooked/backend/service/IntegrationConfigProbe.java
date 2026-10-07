package com.cooked.backend.service;

import com.cooked.backend.entity.IntegrationKey;
import com.google.firebase.FirebaseApp;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Tells whether each integration has credentials configured (never reads secrets out). */
@Component
public class IntegrationConfigProbe {

    private static final Set<String> PLACEHOLDERS = Set.of("", "sk-placeholder-key-replace-me", "VOTRE_SHARED_SECRET", "dummy_cloud", "dummy_key");

    private final Environment env;

    public IntegrationConfigProbe(Environment env) {
        this.env = env;
    }

    public boolean isConfigured(IntegrationKey key) {
        return switch (key) {
            case REVENUECAT -> set("revenuecat.webhook.secret") || set("revenuecat.api.secret");
            case STRIPE -> set("stripe.secret-key");
            case APPLE -> set("apple.iap.shared-secret");
            case GOOGLE_PLAY -> set("google.play.service-account");
            case BREVO -> set("spring.mail.password");
            case OPENAI -> set("openai.api.key");
            case MARKHOR -> set("ai.api.base-url");
            case FIREBASE -> firebaseReady();
            case CLOUDINARY -> set("cloudinary.cloud.name") && set("cloudinary.api.key");
        };
    }

    private boolean set(String property) {
        String v = env.getProperty(property, "");
        return !PLACEHOLDERS.contains(v.trim());
    }

    private static boolean firebaseReady() {
        try {
            return !FirebaseApp.getApps().isEmpty();
        } catch (RuntimeException | NoClassDefFoundError e) {
            return false;
        }
    }
}
