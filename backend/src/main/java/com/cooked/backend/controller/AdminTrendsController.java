package com.cooked.backend.controller;

import com.cooked.backend.dto.response.TrendsResponse;
import com.cooked.backend.service.AdminTrendsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Trend intelligence. HTTP only: the work is in {@link AdminTrendsService}. */
@RestController
@RequestMapping("/api/admin/trends")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Trends", description = "Rising searches, scanned ingredients, saved recipes and categories")
public class AdminTrendsController {

    private final AdminTrendsService trendsService;

    @Operation(summary = "Trends over the last [days] days vs the previous window (days: 1-90, default 30)")
    @GetMapping
    public ResponseEntity<TrendsResponse> trends(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(trendsService.trends(days));
    }
}
