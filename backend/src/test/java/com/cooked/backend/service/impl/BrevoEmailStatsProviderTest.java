package com.cooked.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BrevoEmailStatsProviderTest {

    private static final String JSON = "{\"range\":\"2026-09-08|2026-10-07\",\"requests\":200,\"delivered\":196,\"uniqueOpens\":98,"
            + "\"uniqueClicks\":20,\"hardBounces\":1,\"softBounces\":3,\"spamReports\":1,\"blocked\":0,\"unsubscribed\":2}";

    @Test
    void parsesRatesAndCachesCalls() {
        RestTemplate rest = mock(RestTemplate.class);
        when(rest.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class))).thenReturn(ResponseEntity.ok(JSON));
        BrevoEmailStatsProvider p = new BrevoEmailStatsProvider(rest, new ObjectMapper(), "xkeysib-test");
        LocalDate to = LocalDate.of(2026, 10, 7);

        var s = p.aggregated(to.minusDays(29), to, "trial_ends_tomorrow").orElseThrow();
        assertEquals(98.0, s.getDeliveryRate());
        assertEquals(50.0, s.getOpenRate());
        assertEquals(0.5, s.getSpamRate());
        p.aggregated(to.minusDays(29), to, "trial_ends_tomorrow");
        verify(rest, times(1)).exchange(any(URI.class), any(), any(), eq(String.class));
    }

    @Test
    void unavailableWithoutKeyOrOnError() {
        RestTemplate rest = mock(RestTemplate.class);
        assertTrue(new BrevoEmailStatsProvider(rest, new ObjectMapper(), "").aggregated(LocalDate.now(), LocalDate.now(), null).isEmpty());
        when(rest.exchange(any(URI.class), any(), any(), eq(String.class))).thenThrow(new RuntimeException("401"));
        assertTrue(new BrevoEmailStatsProvider(rest, new ObjectMapper(), "k").aggregated(LocalDate.now(), LocalDate.now(), null).isEmpty());
        verifyNoMoreInteractions(ignoreStubs(rest));
    }
}
