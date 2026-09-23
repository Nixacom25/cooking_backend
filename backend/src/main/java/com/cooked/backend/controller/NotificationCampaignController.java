package com.cooked.backend.controller;

import com.cooked.backend.dto.request.NotificationCampaignRequest;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.dto.response.NotificationCampaignResponse;
import com.cooked.backend.entity.NotificationCampaign;
import com.cooked.backend.service.NotificationCampaignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notification-campaigns")
@RequiredArgsConstructor
@Tag(name = "Notification Campaigns", description = "Endpoints for managing push notification campaigns")
@SecurityRequirement(name = "bearerAuth")
public class NotificationCampaignController {

    private final NotificationCampaignService campaignService;

    @Operation(summary = "Create a new notification campaign")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NotificationCampaignResponse> createCampaign(
            @Valid @RequestBody NotificationCampaignRequest request,
            Authentication auth) {
        request.setCreatedBy(auth.getName());
        return ResponseEntity.ok(campaignService.createCampaign(request));
    }

    @Operation(summary = "Get a specific campaign by ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NotificationCampaignResponse> getCampaign(@PathVariable Long id) {
        return ResponseEntity.ok(campaignService.getCampaign(id));
    }

    @Operation(summary = "Get all notification campaigns")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<NotificationCampaignResponse>> getAllCampaigns() {
        return ResponseEntity.ok(campaignService.getAllCampaigns());
    }

    @Operation(summary = "Get campaigns by status")
    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<NotificationCampaignResponse>> getCampaignsByStatus(
            @PathVariable NotificationCampaign.CampaignStatus status) {
        return ResponseEntity.ok(campaignService.getCampaignsByStatus(status));
    }

    @Operation(summary = "Get campaigns created by current user")
    @GetMapping("/my-campaigns")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<NotificationCampaignResponse>> getMyCampaigns(Authentication auth) {
        return ResponseEntity.ok(campaignService.getCampaignsByCreator(auth.getName()));
    }

    @Operation(summary = "Update a notification campaign")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NotificationCampaignResponse> updateCampaign(
            @PathVariable Long id,
            @Valid @RequestBody NotificationCampaignRequest request) {
        return ResponseEntity.ok(campaignService.updateCampaign(id, request));
    }

    @Operation(summary = "Delete a notification campaign")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> deleteCampaign(@PathVariable Long id) {
        campaignService.deleteCampaign(id);
        return ResponseEntity.ok(new MessageResponse("Campaign deleted successfully"));
    }

    @Operation(summary = "Cancel a scheduled campaign")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> cancelCampaign(@PathVariable Long id) {
        campaignService.cancelCampaign(id);
        return ResponseEntity.ok(new MessageResponse("Campaign cancelled successfully"));
    }

    @Operation(summary = "Send a campaign immediately")
    @PostMapping("/{id}/send-now")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> sendCampaignNow(@PathVariable Long id) {
        campaignService.sendCampaignNow(id);
        return ResponseEntity.ok(new MessageResponse("Campaign sent successfully"));
    }

    @Operation(summary = "Get campaign analytics")
    @GetMapping("/{id}/analytics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NotificationCampaignResponse> getCampaignAnalytics(@PathVariable Long id) {
        return ResponseEntity.ok(campaignService.getCampaignAnalytics(id));
    }

    @Operation(summary = "Track notification open (public endpoint for mobile tracking)")
    @PostMapping("/{id}/open")
    public ResponseEntity<MessageResponse> trackNotificationOpen(@PathVariable Long id) {
        campaignService.trackNotificationOpen(id);
        return ResponseEntity.ok(new MessageResponse("Open tracked"));
    }

    @Operation(summary = "Track notification click (public endpoint for mobile tracking)")
    @PostMapping("/{id}/click")
    public ResponseEntity<MessageResponse> trackNotificationClick(@PathVariable Long id) {
        campaignService.trackNotificationClick(id);
        return ResponseEntity.ok(new MessageResponse("Click tracked"));
    }
}