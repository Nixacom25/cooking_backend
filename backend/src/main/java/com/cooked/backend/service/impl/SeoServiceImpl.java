package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.SeoOverviewResponse;
import com.cooked.backend.dto.response.SeoOverviewResponse.PageStats;
import com.cooked.backend.dto.response.SeoOverviewResponse.SearchTotals;
import com.cooked.backend.dto.response.TrendsResponse;
import com.cooked.backend.entity.Article;
import com.cooked.backend.entity.ArticleStatus;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.ArticleRepository;
import com.cooked.backend.repository.TrendQueryRepository;
import com.cooked.backend.service.AdminTrendsService;
import com.cooked.backend.service.SearchConsoleClient;
import com.cooked.backend.service.SeoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeoServiceImpl implements SeoService {

    /** Search Console data lags about 2 days. */
    static final int LAG_DAYS = 2;

    private final SearchConsoleClient searchConsole;
    private final ArticleRepository articles;
    private final AdminTrendsService trends;
    private final TrendQueryRepository trendQueries;

    @Override
    public SeoOverviewResponse overview(int days) {
        int d = Math.max(7, Math.min(days, 90));
        LocalDate to = LocalDate.now().minusDays(LAG_DAYS);
        LocalDate from = to.minusDays(d - 1L);
        Map<String, Article> bySlug = new HashMap<>();
        articles.findAll().forEach(a -> bySlug.put(a.getSlug(), a));

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (ArticleStatus s : ArticleStatus.values()) byStatus.put(s.name(), 0L);
        articles.countByStatus().forEach(c -> byStatus.put(c.getStatus().name(), c.getTotal() == null ? 0 : c.getTotal()));
        LocalDateTime cutoff = LocalDateTime.now().minusDays(d);
        long livePrev = bySlug.values().stream().filter(a -> a.getStatus() == ArticleStatus.PUBLISHED && a.getPublishedAt() != null && a.getPublishedAt().isBefore(cutoff)).count();

        return SeoOverviewResponse.builder()
                .days(d)
                .searchConsoleConnected(searchConsole.isConfigured())
                .siteUrl(searchConsole.siteUrl())
                .current(searchConsole.totals(from, to).map(SeoServiceImpl::totals).orElse(null))
                .previous(searchConsole.totals(from.minusDays(d), from.minusDays(1)).map(SeoServiceImpl::totals).orElse(null))
                .topPages(searchConsole.byPage(from, to, 25).stream().map(r -> page(r, bySlug)).toList())
                .articlesByStatus(byStatus)
                .articlesLive(byStatus.get(ArticleStatus.PUBLISHED.name()))
                .articlesLivePrev(livePrev)
                .opportunity(opportunity(bySlug.values()))
                .build();
    }

    @Override
    public PageStats articlePerformance(UUID articleId, int days) {
        Article a = articles.findById(articleId).orElseThrow(() -> new ResourceNotFoundException("Article not found"));
        if (a.getStatus() != ArticleStatus.PUBLISHED) return null;
        LocalDate to = LocalDate.now().minusDays(LAG_DAYS);
        return searchConsole.byPage(to.minusDays(Math.max(7, Math.min(days, 90)) - 1L), to, 250).stream()
                .filter(r -> slugOf(r.key()).map(a.getSlug()::equals).orElse(false))
                .findFirst().map(r -> page(r, Map.of(a.getSlug(), a))).orElse(null);
    }

    private SeoOverviewResponse.Opportunity opportunity(Collection<Article> all) {
        TrendsResponse t = trends.trends(30);
        if (t.getSearches() == null || t.getSearches().isEmpty()) return null;
        TrendsResponse.Trend top = t.getSearches().get(0);
        String kw = top.getLabel().toLowerCase(Locale.ROOT);
        boolean hasArticle = all.stream().anyMatch(a -> a.getPrimaryKeyword() != null && a.getPrimaryKeyword().toLowerCase(Locale.ROOT).contains(kw));
        return SeoOverviewResponse.Opportunity.builder().keyword(top.getLabel()).growth(top.getGrowth()).searches(top.getTotal())
                .recipes(trendQueries.countRecipesMatching(kw)).hasArticle(hasArticle).build();
    }

    static Optional<String> slugOf(String pageUrl) {
        int i = pageUrl == null ? -1 : pageUrl.indexOf("/blog/");
        if (i < 0) return Optional.empty();
        String s = pageUrl.substring(i + 6);
        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(0, q);
        return Optional.of(s.endsWith("/") ? s.substring(0, s.length() - 1) : s);
    }

    private static SearchTotals totals(SearchConsoleClient.Row r) {
        return SearchTotals.builder().clicks(r.clicks()).impressions(r.impressions()).ctr(r.ctr()).position(r.position()).build();
    }

    private static PageStats page(SearchConsoleClient.Row r, Map<String, Article> bySlug) {
        Article a = slugOf(r.key()).map(bySlug::get).orElse(null);
        return PageStats.builder().page(r.key()).articleTitle(a == null ? null : a.getTitle()).articleId(a == null ? null : a.getId().toString())
                .clicks(r.clicks()).impressions(r.impressions()).ctr(r.ctr()).position(r.position()).build();
    }
}
