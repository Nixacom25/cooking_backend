package com.cooked.backend.security;

import com.cooked.backend.entity.IntegrationKey;
import com.cooked.backend.service.IntegrationEventRecorder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebhookLoggingFilterTest {

    private final IntegrationEventRecorder recorder = mock(IntegrationEventRecorder.class);
    private final WebhookLoggingFilter filter = new WebhookLoggingFilter(recorder, new ObjectMapper());

    @Test
    void mapsWebhookPaths() {
        assertEquals(IntegrationKey.REVENUECAT, WebhookLoggingFilter.keyFor("/api/webhooks/revenuecat"));
        assertEquals(IntegrationKey.REVENUECAT, WebhookLoggingFilter.keyFor("/subscriptions/revenuecat-webhook"));
        assertEquals(IntegrationKey.STRIPE, WebhookLoggingFilter.keyFor("/webhooks/stripe/"));
        assertNull(WebhookLoggingFilter.keyFor("/api/recipes"));
    }

    @Test
    void readsEventTypes() {
        assertEquals("RENEWAL", filter.eventName(IntegrationKey.REVENUECAT, "{\"event\":{\"type\":\"RENEWAL\"}}"));
        assertEquals("checkout.session.completed", filter.eventName(IntegrationKey.STRIPE, "{\"type\":\"checkout.session.completed\"}"));
        String claims = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"notificationType\":\"DID_RENEW\"}".getBytes(StandardCharsets.UTF_8));
        assertEquals("DID_RENEW", filter.eventName(IntegrationKey.APPLE, "{\"signedPayload\":\"h." + claims + ".s\"}"));
        String data = Base64.getEncoder().encodeToString("{\"subscriptionNotification\":{\"notificationType\":4}}".getBytes(StandardCharsets.UTF_8));
        assertEquals("subscription:4", filter.eventName(IntegrationKey.GOOGLE_PLAY, "{\"message\":{\"data\":\"" + data + "\"}}"));
        assertEquals("notification", filter.eventName(IntegrationKey.STRIPE, "not json"));
    }

    @Test
    void recordsOutcomeOfWebhookRequests() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/webhooks/revenuecat");
        req.setContent("{\"event\":{\"type\":\"BILLING_ISSUE\"}}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
            @Override
            protected void service(jakarta.servlet.http.HttpServletRequest r, jakarta.servlet.http.HttpServletResponse s) throws java.io.IOException {
                r.getInputStream().readAllBytes();                    // controller reads the body
                s.setStatus(502);
            }
        });
        filter.doFilter(req, res, chain);
        verify(recorder).record(eq(IntegrationKey.REVENUECAT), eq("BILLING_ISSUE"), eq(false), eq(502), anyInt(), eq("HTTP 502"));

        MockHttpServletRequest other = new MockHttpServletRequest("POST", "/api/recipes");
        filter.doFilter(other, new MockHttpServletResponse(), new MockFilterChain());
        verifyNoMoreInteractions(recorder);
    }
}
