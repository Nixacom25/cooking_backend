package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.ProductAnalyticsResponse;
import com.cooked.backend.entity.ProductEventType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdminAnalyticsServiceImplTest {

    @Test
    void daysAreClamped() {
        assertEquals(1, AdminAnalyticsServiceImpl.clamp(0));
        assertEquals(30, AdminAnalyticsServiceImpl.clamp(30));
        assertEquals(90, AdminAnalyticsServiceImpl.clamp(10_000));
    }

    @Test
    void everyTypeIsPresentEvenWithoutEvents() {
        List<ProductAnalyticsResponse.TypeStats> s = AdminAnalyticsServiceImpl.summaries(List.of());
        assertEquals(ProductEventType.values().length, s.size());
        assertTrue(s.stream().allMatch(t -> t.getTotal() == 0 && t.getSuccessRate() == null));
    }

    @Test
    void dailySeriesIsZeroFilled() {
        var daily = AdminAnalyticsServiceImpl.daily(LocalDate.of(2026, 10, 1), 6, List.of());
        assertEquals(6, daily.size());
        assertEquals("2026-10-06", daily.get(5).getDate());
    }

    @Test
    void discoverySourcesAreReadable() {
        assertEquals("Not answered", AdminAnalyticsServiceImpl.sourceLabel(null));
        assertEquals("Friend referral", AdminAnalyticsServiceImpl.sourceLabel("FRIEND_REFERRAL"));
    }
}
