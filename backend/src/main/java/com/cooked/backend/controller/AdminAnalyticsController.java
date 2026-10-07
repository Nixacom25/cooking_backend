package com.cooked.backend.controller;

import com.cooked.backend.dto.response.AcquisitionResponse;
import com.cooked.backend.dto.response.EngagementResponse;
import com.cooked.backend.dto.response.ProductAnalyticsResponse;
import com.cooked.backend.service.AdminAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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

    @Operation(summary = "Scan / import / web-search metrics (days: 1-90, default 30)")
    @GetMapping("/product")
    public ResponseEntity<ProductAnalyticsResponse> product(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(analyticsService.product(days));
    }

    @Operation(summary = "Sign-ups and discovery sources (days: 1-90, default 30)")
    @GetMapping("/acquisition")
    public ResponseEntity<AcquisitionResponse> acquisition(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(analyticsService.acquisition(days));
    }

    @Operation(summary = "Active users: DAU / WAU / MAU and daily series (days: 1-90, default 30)")
    @GetMapping("/engagement")
    public ResponseEntity<EngagementResponse> engagement(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(analyticsService.engagement(days));
    }
}
