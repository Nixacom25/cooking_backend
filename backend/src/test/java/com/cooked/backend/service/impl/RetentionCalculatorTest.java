package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.RetentionResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RetentionCalculatorTest {

    private final LocalDate today = LocalDate.of(2026, 10, 7);
    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();

    @Test
    void rateCountsOnlyEligibleUsersAndActivityOnOrAfterTheMark() {
        var members = List.of(
                new RetentionCalculator.Member(a, today.minusDays(10)),   // eligible D1, D7
                new RetentionCalculator.Member(b, today.minusDays(8)),    // eligible D1, D7
                new RetentionCalculator.Member(c, today.minusDays(2)));   // eligible D1 only
        Map<UUID, Set<LocalDate>> active = Map.of(
                a, Set.of(today.minusDays(9), today),      // day 1 and day 10
                b, Set.of(today.minusDays(8)),             // only signup day
                c, Set.of(today.minusDays(1)));            // day 1

        assertEquals(66.7, RetentionCalculator.rate(members, active, today, 1));
        assertEquals(50.0, RetentionCalculator.rate(members, active, today, 7));
        assertNull(RetentionCalculator.rate(members, active, today, 14));
    }

    @Test
    void groupsByLabelBiggestFirstOrChronologicalForWeeks() {
        var members = List.of(
                new RetentionCalculator.Member(a, today.minusDays(20)),
                new RetentionCalculator.Member(b, today.minusDays(3)),
                new RetentionCalculator.Member(c, today.minusDays(2)));
        List<RetentionResponse.Group> bySize = RetentionCalculator.groups(members, Map.of(), today,
                m -> m.id().equals(a) ? "TikTok" : "Instagram", false);
        assertEquals("Instagram", bySize.get(0).label());
        assertEquals(2, bySize.get(0).users());
        assertEquals(0.0, bySize.get(0).d1());

        List<RetentionResponse.Group> weeks = RetentionCalculator.groups(members, Map.of(), today,
                m -> RetentionCalculator.weekOf(m.signup()), true);
        assertEquals("Week of Sep 14", weeks.get(0).label());
        assertEquals("Week of Oct 5", weeks.get(weeks.size() - 1).label());
    }
}
