package com.cooked.backend.controller;

import com.cooked.backend.dto.response.RevenueSummaryResponse;
import com.cooked.backend.service.AdminRevenueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Revenue & subscription metrics for the admin backoffice. HTTP only: the work is in {@link AdminRevenueService}. */
@RestController
@RequestMapping("/api/admin/revenue")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Revenue", description = "Revenue and subscription metrics for administrators")
public class AdminRevenueController {

    private final AdminRevenueService adminRevenueService;

    @Operation(summary = "Revenue and subscription summary (last N days vs the N before, default 30)")
    @GetMapping("/summary")
    public ResponseEntity<RevenueSummaryResponse> summary(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(adminRevenueService.getSummary(days));
    }
}
