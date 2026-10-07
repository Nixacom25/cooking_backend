package com.cooked.backend.controller;

import com.cooked.backend.dto.response.EmailSummaryResponse;
import com.cooked.backend.dto.response.IntegrationDetailResponse;
import com.cooked.backend.dto.response.IntegrationStatusResponse;
import com.cooked.backend.service.AdminIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Integrations and email delivery. HTTP only: the work is in {@link AdminIntegrationService}. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Integrations", description = "External services: configuration, webhooks, emails")
public class AdminIntegrationController {

    private final AdminIntegrationService integrationService;

    @Operation(summary = "Every integration: configured or not, activity over 24h / 30d")
    @GetMapping("/integrations")
    public ResponseEntity<List<IntegrationStatusResponse>> integrations() {
        return ResponseEntity.ok(integrationService.integrations());
    }

    @Operation(summary = "One integration: success rate, events by type, recent events (size ≤ 100)")
    @GetMapping("/integrations/{key}")
    public ResponseEntity<IntegrationDetailResponse> integration(@PathVariable String key,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(integrationService.integration(key, page, size));
    }

    @Operation(summary = "Emails sent through Brevo, by template (days: 1-90, default 30)")
    @GetMapping("/email/summary")
    public ResponseEntity<EmailSummaryResponse> emails(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(integrationService.emails(days));
    }
}
