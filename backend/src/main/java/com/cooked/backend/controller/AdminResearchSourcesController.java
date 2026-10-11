package com.cooked.backend.controller;

import com.cooked.backend.dto.response.ResearchSourcesResponse;
import com.cooked.backend.service.ResearchSourcesService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/** SEO & content → Research sources. HTTP only: the work is in {@link ResearchSourcesService}. */
@RestController
@RequestMapping("/api/admin/research-sources")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin research sources", description = "Search Console status and competitor sitemaps")
public class AdminResearchSourcesController {

    private final ResearchSourcesService service;

    @GetMapping
    public ResponseEntity<ResearchSourcesResponse> overview() {
        return ResponseEntity.ok(service.overview());
    }

    @PostMapping("/search-console/test")
    public ResponseEntity<Map<String, Boolean>> testSearchConsole() {
        return ResponseEntity.ok(Map.of("ok", service.testSearchConsole()));
    }

    public record UrlRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 255) String url) {
    }

    @PostMapping("/competitors")
    public ResponseEntity<ResearchSourcesResponse.Competitor> add(@jakarta.validation.Valid @RequestBody UrlRequest body, Authentication auth) {
        return ResponseEntity.ok(service.addCompetitor(body.url(), auth.getName()));
    }

    @DeleteMapping("/competitors/{id}")
    public ResponseEntity<Void> remove(@PathVariable UUID id, Authentication auth) {
        service.removeCompetitor(id, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/competitors/{id}/crawl")
    public ResponseEntity<Void> crawl(@PathVariable UUID id) {
        service.crawlNow(id);
        return ResponseEntity.accepted().build();
    }
}
