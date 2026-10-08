package com.cooked.backend.service;

import com.cooked.backend.dto.response.SourceDetailResponse;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Aggregates behind the admin tables' columns (per channel, creator, recipe, article, feature). */
public interface AdminInsightsService {

    List<SourceDetailResponse> sources(int days);

    /** {creatorId: {savers, views30d}} */
    Map<UUID, Map<String, Long>> creators(Collection<UUID> ids);

    /** {recipeId: {views30d, cookbooks, mealPlans}} */
    Map<UUID, Map<String, Long>> recipes(Collection<UUID> ids);

    /** {"/blog/slug": visitors} over the last [days] days. */
    Map<String, Long> articleVisitors(int days);

    /** {eventType: paying users} over the last [days] days. */
    Map<String, Long> payingUsersByFeature(int days);
}
