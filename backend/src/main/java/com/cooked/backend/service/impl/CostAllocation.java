package com.cooked.backend.service.impl;

import com.cooked.backend.entity.CostEntry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** Spreads a manual cost over the days it covers (pure, no I/O). */
final class CostAllocation {

    private CostAllocation() {}

    /** USD charged on [day] by [entry]. */
    static BigDecimal on(CostEntry entry, LocalDate day) {
        if (day.isBefore(entry.getStartDate())) return BigDecimal.ZERO;
        if (entry.getEndDate() != null && day.isAfter(entry.getEndDate())) return BigDecimal.ZERO;
        BigDecimal usd = entry.getAmountUsd();
        return switch (entry.getFrequency()) {
            case ONE_TIME -> day.equals(entry.getStartDate()) ? usd : BigDecimal.ZERO;
            case MONTHLY -> usd.divide(BigDecimal.valueOf(day.lengthOfMonth()), 6, RoundingMode.HALF_UP);
            case ANNUAL -> usd.divide(BigDecimal.valueOf(day.lengthOfYear()), 6, RoundingMode.HALF_UP);
        };
    }
}
