package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.ArticleRequest;
import com.cooked.backend.dto.response.ArticleResponse;
import com.cooked.backend.dto.response.PagedResponse;
import com.cooked.backend.entity.Article;
import com.cooked.backend.entity.ArticleStatus;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.ArticleRepository;
import com.cooked.backend.service.ArticleService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ArticleServiceImpl implements ArticleService {

    static final int MAX_PAGE = 100;

    private final ArticleRepository articles;
    private final String siteUrl;

    public ArticleServiceImpl(ArticleRepository articles, @Value("${site.url:https://cookedapp.com}") String siteUrl) {
        this.articles = articles;
        this.siteUrl = siteUrl.endsWith("/") ? siteUrl.substring(0, siteUrl.length() - 1) : siteUrl;
    }

    @Override
    public PagedResponse<ArticleResponse> list(ArticleStatus status, int page, int size) {
        PageRequest p = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, MAX_PAGE)));
        return PagedResponse.of(status == null ? articles.findAllByOrderByUpdatedAtDesc(p) : articles.findByStatusOrderByUpdatedAtDesc(status, p),
                a -> toResponse(a, false));
    }

    @Override
    public ArticleResponse get(UUID id) {
        return toResponse(find(id), true);
    }

    @Override
    @Transactional
    public ArticleResponse create(ArticleRequest r, String adminEmail) {
        Article a = Article.builder().status(ArticleStatus.IDEA).owner(adminEmail).build();
        apply(a, r);
        return toResponse(articles.save(a), true);
    }

    @Override
    @Transactional
    public ArticleResponse update(UUID id, ArticleRequest r) {
        Article a = find(id);
        apply(a, r);
        return toResponse(articles.save(a), true);
    }

    @Override
    @Transactional
    public ArticleResponse moveTo(UUID id, ArticleStatus status) {
        Article a = find(id);
        if (status == ArticleStatus.PUBLISHED || status == ArticleStatus.SCHEDULED) {
            if (a.getBody() == null || a.getBody().isBlank()) throw new BadRequestException("Write the article before publishing it.");
            if (a.getSummary() == null || a.getSummary().isBlank()) throw new BadRequestException("Add a summary (shown in search results) before publishing.");
        }
        if (status == ArticleStatus.SCHEDULED && (a.getScheduledAt() == null || a.getScheduledAt().isBefore(LocalDateTime.now()))) {
            throw new BadRequestException("Set a future publication date to schedule the article.");
        }
        if (status == ArticleStatus.PUBLISHED && a.getPublishedAt() == null) a.setPublishedAt(LocalDateTime.now());
        a.setStatus(status);
        return toResponse(articles.save(a), true);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        articles.delete(find(id));
    }

    @Override
    public PagedResponse<ArticleResponse> published(int page, int size) {
        return PagedResponse.of(articles.findByStatusOrderByPublishedAtDesc(ArticleStatus.PUBLISHED,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 50)))), a -> toResponse(a, false));
    }

    @Override
    public ArticleResponse publishedBySlug(String slug) {
        return articles.findBySlugAndStatus(slug, ArticleStatus.PUBLISHED).map(a -> toResponse(a, true))
                .orElseThrow(() -> new ResourceNotFoundException("Article not found"));
    }

    @Override
    @Transactional
    public int publishDue() {
        var due = articles.findByStatusAndScheduledAtLessThanEqual(ArticleStatus.SCHEDULED, LocalDateTime.now());
        due.forEach(a -> {
            a.setStatus(ArticleStatus.PUBLISHED);
            if (a.getPublishedAt() == null) a.setPublishedAt(LocalDateTime.now());
        });
        articles.saveAll(due);
        return due.size();
    }

    private void apply(Article a, ArticleRequest r) {
        a.setTitle(r.getTitle().trim());
        String wanted = slugify(r.getSlug() == null || r.getSlug().isBlank() ? r.getTitle() : r.getSlug());
        if (wanted.isEmpty()) throw new BadRequestException("The title needs at least one letter or digit.");
        if (!wanted.equals(a.getSlug())) a.setSlug(uniqueSlug(wanted));
        a.setCategory(blankToNull(r.getCategory()));
        a.setPrimaryKeyword(blankToNull(r.getPrimaryKeyword()));
        a.setSummary(blankToNull(r.getSummary()));
        a.setBody(r.getBody());
        a.setCoverImage(blankToNull(r.getCoverImage()));
        a.setWordTarget(r.getWordTarget());
        a.setScheduledAt(r.getScheduledAt());
    }

    private String uniqueSlug(String base) {
        String slug = base;
        for (int i = 2; articles.existsBySlug(slug); i++) slug = base + "-" + i;
        return slug;
    }

    static String slugify(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        n = n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return n.length() > 160 ? n.substring(0, 160).replaceAll("-$", "") : n;
    }

    static int wordCount(String body) {
        if (body == null || body.isBlank()) return 0;
        return body.replaceAll("[#*_>`\\[\\]()!-]", " ").trim().split("\\s+").length;
    }

    private Article find(UUID id) {
        return articles.findById(id).orElseThrow(() -> new ResourceNotFoundException("Article not found"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    ArticleResponse toResponse(Article a, boolean withBody) {
        int words = wordCount(a.getBody());
        return ArticleResponse.builder()
                .id(a.getId()).title(a.getTitle()).slug(a.getSlug()).status(a.getStatus()).category(a.getCategory())
                .primaryKeyword(a.getPrimaryKeyword()).summary(a.getSummary()).body(withBody ? a.getBody() : null)
                .coverImage(a.getCoverImage()).wordTarget(a.getWordTarget()).wordCount(words).readMinutes(Math.max(1, Math.round(words / 220f)))
                .owner(a.getOwner()).scheduledAt(a.getScheduledAt()).publishedAt(a.getPublishedAt()).updatedAt(a.getUpdatedAt())
                .url(a.getStatus() == ArticleStatus.PUBLISHED ? siteUrl + "/blog/" + a.getSlug() : null)
                .build();
    }
}
