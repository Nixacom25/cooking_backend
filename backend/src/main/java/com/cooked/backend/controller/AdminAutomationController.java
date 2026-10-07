package com.cooked.backend.controller;

import com.cooked.backend.dto.response.AutomationResponse;
import com.cooked.backend.service.AdminAutomationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Automations. HTTP only: the work is in {@link AdminAutomationService}. */
@RestController
@RequestMapping("/api/admin/automations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Automations", description = "Scheduled jobs and event-triggered flows run by the backend")
public class AdminAutomationController {

    private final AdminAutomationService automationService;

    @Operation(summary = "Every automation with its trigger, actions and last 30 days of runs")
    @GetMapping
    public ResponseEntity<List<AutomationResponse>> automations() {
        return ResponseEntity.ok(automationService.automations());
    }
}
