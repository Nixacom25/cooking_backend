package com.cooked.backend.controller;

import com.cooked.backend.dto.response.SourceDetailResponse;
import com.cooked.backend.service.AdminInsightsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Columns of the admin tables (batched per page). HTTP only. */
@RestController
@RequestMapping("/api/admin/insights")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Insights", description = "Per channel, creator, recipe, article and feature aggregates")
public class AdminInsightsController {

    private final AdminInsightsService insights;

    @Operation(summary = "Acquisition channels: sign-ups, trials, paying users, revenue and LTV per discovery source")
    @GetMapping("/sources")
    public ResponseEntity<List<SourceDetailResponse>> sources(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(insights.sources(days));
    }

    @Operation(summary = "Savers and 30-day views per creator (max 100 ids)")
    @GetMapping("/creators")
    public ResponseEntity<Map<UUID, Map<String, Long>>> creators(@RequestParam List<UUID> ids) {
        return ResponseEntity.ok(insights.creators(ids));
    }

    @Operation(summary = "30-day views, cookbooks and meal plans per recipe (max 100 ids)")
    @GetMapping("/recipes")
    public ResponseEntity<Map<UUID, Map<String, Long>>> recipes(@RequestParam List<UUID> ids) {
        return ResponseEntity.ok(insights.recipes(ids));
    }

    @Operation(summary = "Website visitors per blog article path")
    @GetMapping("/articles")
    public ResponseEntity<Map<String, Long>> articles(@RequestParam(defaultValue = "28") int days) {
        return ResponseEntity.ok(insights.articleVisitors(days));
    }

    @Operation(summary = "Paying users per product feature (event type)")
    @GetMapping("/paying-by-feature")
    public ResponseEntity<Map<String, Long>> payingByFeature(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(insights.payingUsersByFeature(days));
    }
}
