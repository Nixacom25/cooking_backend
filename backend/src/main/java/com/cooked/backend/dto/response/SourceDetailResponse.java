package com.cooked.backend.dto.response;

/** Acquisition channel (onboarding discovery source) over a window. Revenue = lifetime payments of those users (EUR). */
public record SourceDetailResponse(String source, long signups, long trials, long paid, double revenue, Double ltv) {
}
