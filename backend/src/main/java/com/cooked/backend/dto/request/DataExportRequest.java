package com.cooked.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.util.List;

/**
 * Settings → Export data. period: LAST_12_MONTHS, ALL or CUSTOM (from/to);
 * code: the 6-digit code emailed to the admin (exports contain personal data).
 */
public record DataExportRequest(
        @NotEmpty List<@Pattern(regexp = "users|recipes|payments|costs|audit") String> datasets,
        @NotBlank @Pattern(regexp = "CSV|JSON") String format,
        @NotBlank @Pattern(regexp = "LAST_12_MONTHS|ALL|CUSTOM") String period,
        LocalDate from,
        LocalDate to,
        @NotBlank String code) {
}
