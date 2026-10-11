package com.cooked.backend.service;

import com.cooked.backend.dto.response.ResearchSourcesResponse;

import java.util.UUID;

/** Research sources behind SEO trend intelligence: Search Console and competitor sites. */
public interface ResearchSourcesService {

    ResearchSourcesResponse overview();

    /** Checks the Search Console connection with a 7-day query; returns true when it answers. */
    boolean testSearchConsole();

    /** Adds a competitor site (public http(s) address only) and reads its sitemap in the background. */
    ResearchSourcesResponse.Competitor addCompetitor(String url, String adminEmail);

    void removeCompetitor(UUID id, String adminEmail);

    /** Reads the site's sitemap again now (in the background). */
    void crawlNow(UUID id);

    /** Weekly job: reads every competitor's sitemap. */
    void crawlAll();
}
