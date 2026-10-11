package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** A competitor recipe site whose sitemap is read weekly (SEO & content → Research sources). */
@Entity
@Table(name = "competitor_sites")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorSite {

    public enum Status { QUEUED, OK, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Normalised origin, e.g. https://www.allrecipes.com */
    @Column(nullable = false, unique = true, length = 255)
    private String url;

    @Column(nullable = false, length = 255)
    private String domain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    private LocalDateTime lastCrawledAt;

    /** URLs listed in the sitemap(s) and how many look like recipes. */
    private Integer pages;
    private Integer recipePages;

    /** Recipe pages added or updated in the 7 days before the crawl (sitemap lastmod). */
    private Integer newThisWeek;

    /** Up to 5 recent recipe titles (from URL slugs), one per line. */
    @Column(columnDefinition = "TEXT")
    private String latestTitles;

    @Column(length = 300)
    private String lastError;

    @Column(length = 160)
    private String addedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
