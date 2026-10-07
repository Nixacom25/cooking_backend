package com.cooked.backend.service.impl;

import com.cooked.backend.entity.CostCategory;
import com.cooked.backend.entity.CostEntry;
import com.cooked.backend.entity.CostFrequency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class CostAllocationTest {

    private static CostEntry entry(CostFrequency f, String usd, LocalDate start, LocalDate end) {
        return CostEntry.builder().provider("Render").category(CostCategory.INFRASTRUCTURE).amount(new BigDecimal(usd))
                .currency("USD").amountUsd(new BigDecimal(usd)).frequency(f).startDate(start).endDate(end).build();
    }

    @Test
    void monthlyIsSpreadOverTheMonthsDays() {
        CostEntry e = entry(CostFrequency.MONTHLY, "310", LocalDate.of(2026, 10, 1), null);
        double sum = 0;
        for (int d = 1; d <= 31; d++) sum += CostAllocation.on(e, LocalDate.of(2026, 10, d)).doubleValue();
        assertEquals(310.0, sum, 0.01);
        assertEquals(0, CostAllocation.on(e, LocalDate.of(2026, 9, 30)).signum());
    }

    @Test
    void endDateAndOneTimeAreRespected() {
        CostEntry ended = entry(CostFrequency.ANNUAL, "365", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
        assertEquals(1.0, CostAllocation.on(ended, LocalDate.of(2026, 3, 3)).doubleValue(), 0.0001);
        assertEquals(0, CostAllocation.on(ended, LocalDate.of(2026, 7, 1)).signum());

        CostEntry once = entry(CostFrequency.ONE_TIME, "99", LocalDate.of(2026, 10, 5), null);
        assertEquals(99.0, CostAllocation.on(once, LocalDate.of(2026, 10, 5)).doubleValue());
        assertEquals(0, CostAllocation.on(once, LocalDate.of(2026, 10, 6)).signum());
    }
}
