package com.cooked.backend.controller;

import com.cooked.backend.dto.request.ArticleRequest;
import com.cooked.backend.dto.response.ArticleResponse;
import com.cooked.backend.dto.response.PagedResponse;
import com.cooked.backend.dto.response.SeoOverviewResponse;
import com.cooked.backend.entity.ArticleStatus;
import com.cooked.backend.service.ArticleService;
import com.cooked.backend.service.SeoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/** Articles and SEO. HTTP only: the work is in {@link ArticleService} and {@link SeoService}. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Content & SEO", description = "Website articles workflow and organic search")
public class AdminArticleController {

    private final ArticleService articles;
    private final SeoService seo;

    @Operation(summary = "Search Console totals, top pages, content pipeline, opportunity (days: 7-90)")
    @GetMapping("/seo/overview")
    public ResponseEntity<SeoOverviewResponse> overview(@RequestParam(defaultValue = "28") int days) {
        return ResponseEntity.ok(seo.overview(days));
    }

    @GetMapping("/articles")
    public ResponseEntity<PagedResponse<ArticleResponse>> list(@RequestParam(required = false) ArticleStatus status,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(articles.list(status, page, size));
    }

    @GetMapping("/articles/{id}")
    public ResponseEntity<ArticleResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(articles.get(id));
    }

    @Operation(summary = "Search Console figures for one published article")
    @GetMapping("/articles/{id}/performance")
    public ResponseEntity<SeoOverviewResponse.PageStats> performance(@PathVariable UUID id, @RequestParam(defaultValue = "28") int days) {
        SeoOverviewResponse.PageStats stats = seo.articlePerformance(id, days);
        return stats == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(stats);
    }

    @PostMapping("/articles")
    public ResponseEntity<ArticleResponse> create(@Valid @RequestBody ArticleRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(articles.create(request, auth.getName()));
    }

    @PutMapping("/articles/{id}")
    public ResponseEntity<ArticleResponse> update(@PathVariable UUID id, @Valid @RequestBody ArticleRequest request) {
        return ResponseEntity.ok(articles.update(id, request));
    }

    @Operation(summary = "Move an article in the workflow (IDEA … PUBLISHED)")
    @PostMapping("/articles/{id}/status")
    public ResponseEntity<ArticleResponse> status(@PathVariable UUID id, @RequestBody Map<String, ArticleStatus> body) {
        return ResponseEntity.ok(articles.moveTo(id, body.get("status")));
    }

    @DeleteMapping("/articles/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        articles.delete(id);
        return ResponseEntity.noContent().build();
    }
}
