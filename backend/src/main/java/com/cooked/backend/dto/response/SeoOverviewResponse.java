package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/** Organic search (Google Search Console, when connected) + content pipeline. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeoOverviewResponse {
    private int days;
    private boolean searchConsoleConnected;
    private String siteUrl;
    private SearchTotals current;
    private SearchTotals previous;
    private List<PageStats> topPages;
    private Map<String, Long> articlesByStatus;
    private long articlesLive;
    private long articlesLivePrev;
    private Opportunity opportunity;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SearchTotals {
        private double clicks;
        private double impressions;
        /** 0-100. */
        private double ctr;
        private double position;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PageStats {
        private String page;
        private String articleTitle;
        private String articleId;
        private double clicks;
        private double impressions;
        private double ctr;
        private double position;
    }

    /** Fastest-rising Cooked search, with how many recipes already match it. */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Opportunity {
        private String keyword;
        private Double growth;
        private long searches;
        private long recipes;
        private boolean hasArticle;
    }
}
