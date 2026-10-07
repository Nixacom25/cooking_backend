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
    void activitySeriesIsZeroFilled() {
        var series = AdminAnalyticsServiceImpl.daySeries(LocalDate.of(2026, 10, 1), 7, List.of());
        assertEquals(7, series.size());
        assertEquals("2026-10-07", series.get(6).getDate());
        assertTrue(series.stream().allMatch(d -> d.getTotal() == 0));
    }

    @Test
    void sameLookingSourcesAreMerged() {
        java.util.function.BiFunction<String, Long, com.cooked.backend.repository.UserRepository.LabelCount> lc = (l, n) -> new com.cooked.backend.repository.UserRepository.LabelCount() {
            public String getLabel() { return l; }
            public Long getTotal() { return n; }
        };
        var merged = AdminAnalyticsServiceImpl.mergeSources(List.of(lc.apply(null, 20L), lc.apply("", 11L), lc.apply("TIKTOK", 3L), lc.apply("tiktok", 2L)));
        assertEquals(2, merged.size());
        assertEquals("Not answered", merged.get(0).getSource());
        assertEquals(31L, merged.get(0).getTotal());
        assertEquals(5L, merged.get(1).getTotal());
    }

    @Test
    void discoverySourcesAreReadable() {
        assertEquals("Not answered", AdminAnalyticsServiceImpl.sourceLabel(null));
        assertEquals("Friend referral", AdminAnalyticsServiceImpl.sourceLabel("FRIEND_REFERRAL"));
    }
}
