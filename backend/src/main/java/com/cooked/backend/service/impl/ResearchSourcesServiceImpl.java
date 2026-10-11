package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.ResearchSourcesResponse;
import com.cooked.backend.entity.CompetitorSite;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.CompetitorSiteRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.ActivityLogService;
import com.cooked.backend.service.ResearchSourcesService;
import com.cooked.backend.service.SearchConsoleClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResearchSourcesServiceImpl implements ResearchSourcesService {

    static final int MAX_SITES = 20;

    private final SearchConsoleClient searchConsole;
    private final CompetitorSiteRepository sites;
    private final CompetitorCrawler crawler;
    private final UserRepository users;
    private final ActivityLogService activityLog;
    /** Self-reference so crawlNow / addCompetitor run the crawl through the @Async proxy. */
    @Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private ResearchSourcesServiceImpl self;

    static final List<ResearchSourcesResponse.Source> OTHER_SOURCES = List.of(
            new ResearchSourcesResponse.Source("google_trends", "Google Trends", "Rising food & recipe searches by country", false,
                    "Google Trends has no public API Cooked can connect to yet."),
            new ResearchSourcesResponse.Source("tiktok", "TikTok", "Trending recipe hashtags and sounds", false,
                    "Needs an approved TikTok Research API account."),
            new ResearchSourcesResponse.Source("instagram", "Instagram", "Top food posts and hashtags", false,
                    "Needs a Meta app with Instagram hashtag search approved."));

    @Override
    public ResearchSourcesResponse overview() {
        return new ResearchSourcesResponse(
                new ResearchSourcesResponse.SearchConsole(searchConsole.isConfigured(), searchConsole.siteUrl()),
                OTHER_SOURCES,
                sites.findAllByOrderByCreatedAtAsc().stream().map(ResearchSourcesServiceImpl::view).toList());
    }

    @Override
    public boolean testSearchConsole() {
        if (!searchConsole.isConfigured()) return false;
        LocalDate to = LocalDate.now().minusDays(2); // Search Console data lags ~2 days
        return searchConsole.totals(to.minusDays(7), to).isPresent();
    }

    @Override
    public ResearchSourcesResponse.Competitor addCompetitor(String raw, String adminEmail) {
        String origin = normalise(raw);
        try {
            CompetitorCrawler.assertPublic(URI.create(origin));
        } catch (Exception e) {
            throw new BadRequestException("Enter a public website address (" + e.getMessage() + ").");
        }
        if (sites.existsByUrl(origin)) throw new BadRequestException("This site is already tracked.");
        if (sites.count() >= MAX_SITES) throw new BadRequestException("Up to " + MAX_SITES + " competitor sites can be tracked.");
        String domain = URI.create(origin).getHost().replaceFirst("^www\\.", "");
        CompetitorSite site = sites.save(CompetitorSite.builder().url(origin).domain(domain).status(CompetitorSite.Status.QUEUED).addedBy(adminEmail).build());
        users.findByEmail(adminEmail).ifPresent(a -> activityLog.logActivity(a, "Competitor site added", domain, site.getId(), "COMPETITOR_SITE"));
        self.crawlAsync(site.getId());
        return view(site);
    }

    @Override
    public void removeCompetitor(UUID id, String adminEmail) {
        CompetitorSite site = sites.findById(id).orElseThrow(() -> new ResourceNotFoundException("Site not found"));
        sites.delete(site);
        users.findByEmail(adminEmail).ifPresent(a -> activityLog.logActivity(a, "Competitor site removed", site.getDomain(), id, "COMPETITOR_SITE"));
    }

    @Override
    public void crawlNow(UUID id) {
        CompetitorSite site = sites.findById(id).orElseThrow(() -> new ResourceNotFoundException("Site not found"));
        site.setStatus(CompetitorSite.Status.QUEUED);
        sites.save(site);
        self.crawlAsync(id);
    }

    @Async
    public void crawlAsync(UUID id) {
        crawl(id);
    }

    @Override
    @Scheduled(cron = "0 0 5 * * MON")
    public void crawlAll() {
        for (CompetitorSite s : sites.findAllByOrderByCreatedAtAsc()) crawl(s.getId());
    }

    void crawl(UUID id) {
        CompetitorSite site = sites.findById(id).orElse(null);
        if (site == null) return;
        try {
            CompetitorCrawler.Result r = crawler.crawl(site.getUrl());
            site.setStatus(CompetitorSite.Status.OK);
            site.setPages(r.pages());
            site.setRecipePages(r.recipePages());
            site.setNewThisWeek(r.newThisWeek());
            site.setLatestTitles(String.join("\n", r.latestTitles()));
            site.setLastError(null);
        } catch (Exception e) {
            site.setStatus(CompetitorSite.Status.FAILED);
            String msg = e.getMessage() == null ? "Crawl failed" : e.getMessage();
            site.setLastError(msg.length() > 290 ? msg.substring(0, 290) : msg);
            log.warn("Competitor crawl failed for {}: {}", site.getDomain(), msg);
        }
        site.setLastCrawledAt(LocalDateTime.now());
        sites.save(site);
    }

    /** "allrecipes.com/x" → "https://allrecipes.com" (origin only). */
    static String normalise(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty()) throw new BadRequestException("Enter a website address.");
        if (!v.matches("(?i)^https?://.*")) v = "https://" + v;
        try {
            URI u = URI.create(v);
            if (u.getHost() == null || !u.getHost().contains(".")) throw new IllegalArgumentException();
            String host = u.getHost().toLowerCase(Locale.ROOT);
            return u.getScheme().toLowerCase(Locale.ROOT) + "://" + host + (u.getPort() > 0 ? ":" + u.getPort() : "");
        } catch (Exception e) {
            throw new BadRequestException("Enter a valid website address, like https://example.com");
        }
    }

    static ResearchSourcesResponse.Competitor view(CompetitorSite s) {
        List<String> latest = s.getLatestTitles() == null || s.getLatestTitles().isBlank() ? List.of() : Arrays.asList(s.getLatestTitles().split("\n"));
        return new ResearchSourcesResponse.Competitor(s.getId(), s.getUrl(), s.getDomain(), s.getStatus().name(), s.getLastCrawledAt(),
                s.getPages(), s.getRecipePages(), s.getNewThisWeek(), latest, s.getLastError());
    }
}
