package com.cooked.backend.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** SEO & content → Research sources: what feeds trend intelligence. */
public record ResearchSourcesResponse(SearchConsole searchConsole, List<Source> otherSources, List<Competitor> competitors) {

    public record SearchConsole(boolean connected, String siteUrl) {
    }

    /** A source Cooked can't connect yet, with the reason. */
    public record Source(String key, String name, String description, boolean connected, String reason) {
    }

    public record Competitor(UUID id, String url, String domain, String status, LocalDateTime lastCrawledAt,
                             Integer pages, Integer recipePages, Integer newThisWeek, List<String> latestTitles, String lastError) {
    }
}
