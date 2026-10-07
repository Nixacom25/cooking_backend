package com.cooked.backend.service;

import com.cooked.backend.dto.response.SeoOverviewResponse;

import java.util.UUID;

/** Organic search + content pipeline for the SEO & content screen. */
public interface SeoService {

    SeoOverviewResponse overview(int days);

    /** Search Console figures for one article's page over [days] (null when not connected / no data). */
    SeoOverviewResponse.PageStats articlePerformance(UUID articleId, int days);
}
