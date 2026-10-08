package com.cooked.backend.dto.response;

/**
 * "Happening now" and daily counters of the admin dashboard.
 * Today = since midnight (server time); yesterday = the full previous day.
 */
public record DashboardLiveResponse(long groceryAddsToday, long groceryAddsYesterday, long mealsPlannedToday, long mealsPlannedYesterday,
                                    int scansInProgress, int importsInProgress, long newSubscriptionsLastHour) {
}
