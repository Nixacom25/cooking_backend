package com.cooked.backend.controller;

import com.cooked.backend.dto.response.RevenueSummaryResponse;
import com.cooked.backend.service.AdminRevenueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
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

    @Operation(summary = "Correct a payment's status (SUCCESS counts as revenue; SANDBOX, DUPLICATE, REFUNDED, FAILED do not)")
    @PatchMapping("/payments/{id}/status")
    public ResponseEntity<com.cooked.backend.dto.response.PaymentStatusResponse> setPaymentStatus(
            @PathVariable java.util.UUID id, @jakarta.validation.Valid @RequestBody com.cooked.backend.dto.request.PaymentStatusRequest body,
            org.springframework.security.core.Authentication auth) {
        return ResponseEntity.ok(adminRevenueService.setPaymentStatus(id, body.status(), body.reason(), auth.getName()));
    }

    @Operation(summary = "Payments: transactions with filters, counts per status and KPIs (only SUCCESS counts as revenue)")
    @GetMapping("/payments")
    public ResponseEntity<com.cooked.backend.dto.response.PaymentListResponse> payments(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to,
            @RequestParam(defaultValue = "ALL") String store,
            @RequestParam(defaultValue = "ALL") String plan,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminRevenueService.listPayments(days, from, to, store, plan, status, q, page, size));
    }
}
