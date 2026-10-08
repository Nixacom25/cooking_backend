package com.cooked.backend.controller;

import com.cooked.backend.dto.response.AcquisitionResponse;
import com.cooked.backend.dto.response.EngagementResponse;
import com.cooked.backend.dto.response.ProductFailuresResponse;
import com.cooked.backend.entity.ProductEventType;
import com.cooked.backend.dto.response.ProductAnalyticsResponse;
import com.cooked.backend.service.AdminAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Product & acquisition analytics. HTTP only: the work is in {@link AdminAnalyticsService}. */
@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Analytics", description = "Scan, import, search and acquisition metrics for administrators")
public class AdminAnalyticsController {

    private final AdminAnalyticsService analyticsService;
    private final com.cooked.backend.service.ImportFailureAdminService importFailures;

    @Operation(summary = "Scan / import / web-search metrics (days: 1-90, default 30)")
    @GetMapping("/product")
    public ResponseEntity<ProductAnalyticsResponse> product(@RequestParam(defaultValue = "30") int days,
                                                            @RequestParam(required = false) String platform,
                                                            @RequestParam(required = false) String subscription,
                                                            @RequestParam(required = false) String source,
                                                            @RequestParam(required = false) String version,
                                                            @RequestParam(required = false) String country) {
        return ResponseEntity.ok(analyticsService.product(days, new com.cooked.backend.dto.request.AnalyticsSegment(platform, subscription, source, version, country)));
    }

    @Operation(summary = "Sign-ups and discovery sources (days: 1-90, default 30)")
    @GetMapping("/acquisition")
    public ResponseEntity<AcquisitionResponse> acquisition(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(analyticsService.acquisition(days));
    }

    @Operation(summary = "Retention D1/D7/D14/D30 of recent sign-ups, by week | source | platform | subscription | scan | import | ambassador")
    @GetMapping("/retention")
    public ResponseEntity<com.cooked.backend.dto.response.RetentionResponse> retention(@RequestParam(required = false) String by,
                                                                                      @RequestParam(defaultValue = "90") int days) {
        return ResponseEntity.ok(analyticsService.retention(by, days));
    }

    @Operation(summary = "Active users: DAU / WAU / MAU and daily series (days: 1-90, default 30)")
    @GetMapping("/engagement")
    public ResponseEntity<EngagementResponse> engagement(@RequestParam(defaultValue = "30") int days,
                                                         @RequestParam(required = false) String platform,
                                                         @RequestParam(required = false) String subscription,
                                                         @RequestParam(required = false) String source,
                                                         @RequestParam(required = false) String version,
                                                         @RequestParam(required = false) String country) {
        return ResponseEntity.ok(analyticsService.engagement(days, new com.cooked.backend.dto.request.AnalyticsSegment(platform, subscription, source, version, country)));
    }

    @Operation(summary = "Grocery lists: items added, users, bought, from recipes, daily series, top ingredients and recipes")
    @GetMapping("/grocery")
    public ResponseEntity<com.cooked.backend.dto.response.GroceryAnalyticsResponse> grocery(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(analyticsService.grocery(days));
    }

    @Operation(summary = "Mark a failed import as handled")
    @PutMapping("/import-failures/{id}/resolve")
    public ResponseEntity<Void> resolveImportFailure(@PathVariable java.util.UUID id) {
        importFailures.resolve(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Retry a failed import for the same user and URL")
    @PostMapping("/import-failures/{id}/retry")
    public ResponseEntity<com.cooked.backend.dto.response.ImportRetryResponse> retryImportFailure(@PathVariable java.util.UUID id) {
        return ResponseEntity.ok(importFailures.retry(id));
    }

    @Operation(summary = "Failed imports, newest first (days: 1-90, default 7; size: 1-100)")
    @GetMapping("/import-failures")
    public ResponseEntity<ProductFailuresResponse> importFailures(@RequestParam(defaultValue = "7") int days,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(analyticsService.failures(ProductEventType.IMPORT, days, page, size));
    }
}
