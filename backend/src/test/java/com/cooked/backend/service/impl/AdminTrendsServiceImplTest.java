package com.cooked.backend.service.impl;

import com.cooked.backend.repository.TrendQueryRepository.LabelCount;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdminTrendsServiceImplTest {

    private static LabelCount lc(String label, long total) {
        return new LabelCount() {
            public String getLabel() { return label; }
            public Long getTotal() { return total; }
        };
    }

    @Test
    void newAndGrowingLabelsComeFirst() {
        var ranked = AdminTrendsServiceImpl.rank(
                List.of(lc("pasta", 100), lc("suya", 20), lc("tofu", 5)),
                labels -> List.of(lc("pasta", 100), lc("suya", 10)));
        assertEquals(List.of("tofu", "suya", "pasta"), ranked.stream().map(t -> t.getLabel()).toList());
        assertNull(ranked.get(0).getGrowth());          // new
        assertEquals(100.0, ranked.get(1).getGrowth());
        assertEquals(0.0, ranked.get(2).getGrowth());
    }

    @Test
    void growthAndSeries() {
        assertEquals(-50.0, AdminTrendsServiceImpl.growth(5, 10));
        assertNull(AdminTrendsServiceImpl.growth(3, 0));
        var s = AdminTrendsServiceImpl.series(java.time.LocalDate.of(2026, 10, 1), 3, List.of());
        assertEquals(3, s.size());
        assertEquals("2026-10-03", s.get(2).getDate());
    }
}
