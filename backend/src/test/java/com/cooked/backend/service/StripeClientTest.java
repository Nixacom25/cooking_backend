package com.cooked.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class StripeClientTest {

    private static final String SECRET = "whsec_test_secret";
    private StripeClient client;

    @BeforeEach
    void setUp() {
        client = new StripeClient();
        ReflectionTestUtils.setField(client, "webhookSecret", SECRET);
    }

    private String sign(String payload, long ts) {
        return "t=" + ts + ",v1=" + HexFormat.of().formatHex(StripeClient.hmacSha256(SECRET, ts + "." + payload));
    }

    @Test
    void acceptsAValidSignature() {
        String payload = "{\"id\":\"evt_1\",\"type\":\"checkout.session.completed\"}";
        long now = Instant.now().getEpochSecond();
        assertEquals("evt_1", client.verifyWebhook(payload, sign(payload, now)).path("id").asText());
    }

    @Test
    void rejectsTamperedPayloadOldEventsAndMissingSignature() {
        String payload = "{\"id\":\"evt_1\"}";
        long now = Instant.now().getEpochSecond();
        String header = sign(payload, now);
        assertThrows(SecurityException.class, () -> client.verifyWebhook("{\"id\":\"evt_2\"}", header));
        assertThrows(SecurityException.class, () -> client.verifyWebhook(payload, sign(payload, now - 3600)));
        assertThrows(SecurityException.class, () -> client.verifyWebhook(payload, null));
        assertThrows(SecurityException.class, () -> client.verifyWebhook(payload, "t=" + now + ",v1=deadbeef"));
    }
}
