package com.cooked.backend.controller;

import com.cooked.backend.dto.response.ArticleResponse;
import com.cooked.backend.dto.response.PagedResponse;
import com.cooked.backend.service.ArticleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/** Published articles for the website blog (read-only, public). */
@RestController
@RequestMapping("/public/articles")
@RequiredArgsConstructor
@Tag(name = "Public articles", description = "Published blog articles")
public class PublicArticleController {

    private final ArticleService articles;

    @GetMapping
    public ResponseEntity<PagedResponse<ArticleResponse>> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "24") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic()).body(articles.published(page, size));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ArticleResponse> get(@PathVariable String slug) {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic()).body(articles.publishedBySlug(slug));
    }
}
