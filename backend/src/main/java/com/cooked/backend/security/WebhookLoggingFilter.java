package com.cooked.backend.security;

import com.cooked.backend.entity.IntegrationKey;
import com.cooked.backend.service.IntegrationEventRecorder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Records every incoming billing webhook (provider, event type, HTTP status,
 * latency) without touching the webhook controllers. Payloads are not stored.
 */
@Component
public class WebhookLoggingFilter extends OncePerRequestFilter {

    private static final Map<String, IntegrationKey> PATHS = Map.of(
            "/webhooks/revenuecat", IntegrationKey.REVENUECAT,
            "/subscriptions/revenuecat-webhook", IntegrationKey.REVENUECAT,
            "/webhooks/stripe", IntegrationKey.STRIPE,
            "/webhooks/apple", IntegrationKey.APPLE,
            "/webhooks/google", IntegrationKey.GOOGLE_PLAY);

    private final IntegrationEventRecorder recorder;
    private final ObjectMapper objectMapper;

    public WebhookLoggingFilter(IntegrationEventRecorder recorder, ObjectMapper objectMapper) {
        this.recorder = recorder;
        this.objectMapper = objectMapper;
    }

    static IntegrationKey keyFor(String uri) {
        if (uri == null) return null;
        String path = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
        return PATHS.entrySet().stream().filter(e -> path.endsWith(e.getKey())).map(Map.Entry::getValue).findFirst().orElse(null);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) || keyFor(request.getRequestURI()) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper(request);
        long start = System.nanoTime();
        try {
            chain.doFilter(wrapped, response);
        } finally {
            int status = response.getStatus();
            int ms = (int) ((System.nanoTime() - start) / 1_000_000);
            IntegrationKey key = keyFor(request.getRequestURI());
            String body = new String(wrapped.getContentAsByteArray(), StandardCharsets.UTF_8);
            boolean ok = status < 400;
            recorder.record(key, eventName(key, body), ok, status, ms, ok ? null : "HTTP " + status);
        }
    }

    /** Best-effort event type from the provider's payload ("notification" when unknown). */
    String eventName(IntegrationKey key, String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String name = switch (key) {
                case REVENUECAT -> root.path("event").path("type").asText(null);
                case STRIPE -> root.path("type").asText(null);
                case APPLE -> jwtClaim(root.path("signedPayload").asText(null), "notificationType");
                case GOOGLE_PLAY -> googleType(root.path("message").path("data").asText(null));
                default -> null;
            };
            return name == null || name.isBlank() ? "notification" : name;
        } catch (Exception e) {
            return "notification";
        }
    }

    private String jwtClaim(String jws, String claim) throws IOException {
        if (jws == null) return null;
        String[] parts = jws.split("\\.");
        if (parts.length < 2) return null;
        return objectMapper.readTree(Base64.getUrlDecoder().decode(parts[1])).path(claim).asText(null);
    }

    private String googleType(String data) throws IOException {
        if (data == null) return null;
        JsonNode n = objectMapper.readTree(Base64.getDecoder().decode(data));
        if (n.has("subscriptionNotification")) return "subscription:" + n.path("subscriptionNotification").path("notificationType").asText();
        if (n.has("oneTimeProductNotification")) return "one_time:" + n.path("oneTimeProductNotification").path("notificationType").asText();
        if (n.has("testNotification")) return "test";
        return null;
    }
}
