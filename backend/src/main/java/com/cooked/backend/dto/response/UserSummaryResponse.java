package com.cooked.backend.dto.response;

/**
 * Users screen KPIs.
 *
 * @param active30 clients who opened the app in the last 30 days
 * @param churned30 subscriptions that ended (cancelled / expired) in the last 30 days
 */
public record UserSummaryResponse(long clients, long active30, long onTrial, long churned30) {
}
