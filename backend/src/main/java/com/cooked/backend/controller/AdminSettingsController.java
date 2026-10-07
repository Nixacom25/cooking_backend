package com.cooked.backend.controller;

import com.cooked.backend.dto.request.UpdateWorkspaceSettingsRequest;
import com.cooked.backend.dto.response.WorkspaceSettingsResponse;
import com.cooked.backend.service.SlackNotifier;
import com.cooked.backend.service.WorkspaceSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Workspace settings. HTTP only: the work is in {@link WorkspaceSettingsService}. */
@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Settings", description = "Alerts, summaries, Slack and display preferences")
public class AdminSettingsController {

    private final WorkspaceSettingsService settingsService;
    private final SlackNotifier slack;

    @Operation(summary = "Current settings (the Slack webhook is never returned in full)")
    @GetMapping
    public ResponseEntity<WorkspaceSettingsResponse> get() {
        return ResponseEntity.ok(settingsService.get());
    }

    @Operation(summary = "Change some settings (null fields unchanged; slackWebhookUrl \"\" removes it)")
    @PatchMapping
    public ResponseEntity<WorkspaceSettingsResponse> update(@Valid @RequestBody UpdateWorkspaceSettingsRequest request) {
        return ResponseEntity.ok(settingsService.update(request));
    }

    @Operation(summary = "Post a test message to the Slack webhook")
    @PostMapping("/slack/test")
    public ResponseEntity<Void> testSlack() {
        slack.post("✅ Cooked backoffice: Slack alerts are connected.");
        return ResponseEntity.accepted().build();
    }
}
