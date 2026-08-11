package com.cooked.backend.controller;

import com.cooked.backend.dto.request.CreateAssignmentRequest;
import com.cooked.backend.dto.request.RejectAssignmentRequest;
import com.cooked.backend.dto.response.*;
import com.cooked.backend.entity.AssignmentStatus;
import com.cooked.backend.service.RecipeAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/recipes")
@RequiredArgsConstructor
@Tag(name = "Recipe Assignments", description = "Admin recipe stats, assignment management & validation workflow")
@SecurityRequirement(name = "bearerAuth")
public class RecipeAssignmentController {

    private final RecipeAssignmentService assignmentService;

    @Operation(summary = "Get recipe statistics dashboard")
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<RecipeStatsResponse> getStats() {
        return ResponseEntity.ok(assignmentService.getRecipeStats());
    }

    @Operation(summary = "Get stagiaires leaderboard ranking")
    @GetMapping("/leaderboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<List<StagiaireLeaderboardResponse>> getLeaderboard() {
        return ResponseEntity.ok(assignmentService.getStagiairesLeaderboard());
    }

    @Operation(summary = "Assign recipes to editors")
    @PostMapping("/assignments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<RecipeAssignmentResponse>> createAssignments(
            @RequestBody CreateAssignmentRequest request,
            Authentication auth) {
        return ResponseEntity.ok(assignmentService.createAssignments(request, auth.getName()));
    }

    @Operation(summary = "Assign a specific quantity of unassigned recipes to a stagiaire")
    @PostMapping("/assignments/batch")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<RecipeAssignmentResponse>> assignBatch(
            @RequestBody com.cooked.backend.dto.request.BatchAssignmentRequest request,
            Authentication auth) {
        return ResponseEntity.ok(assignmentService.assignBatchByCount(request, auth.getName()));
    }

    @Operation(summary = "Get count of unassigned unmodified recipes")
    @GetMapping("/assignments/unassigned-count")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Long> getUnassignedCount() {
        return ResponseEntity.ok(assignmentService.getAvailableUnassignedCount());
    }

    @Operation(summary = "Get all recipe assignments")
    @GetMapping("/assignments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<RecipeAssignmentResponse>> getAllAssignments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("assignedDate").descending());
        return ResponseEntity.ok(assignmentService.getAllAssignments(pageable));
    }

    @Operation(summary = "Get assignments filtered by status (path param)")
    @GetMapping("/assignments/status/{status}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<RecipeAssignmentResponse>> getAssignmentsByStatus(
            @PathVariable AssignmentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("assignedDate").descending());
        return ResponseEntity.ok(assignmentService.getAssignmentsByStatus(status, pageable));
    }

    @Operation(summary = "Get assignments filtered by status (query param)")
    @GetMapping("/assignments/by-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<RecipeAssignmentResponse>> getAssignmentsByStatusQuery(
            @RequestParam AssignmentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("assignedDate").descending());
        return ResponseEntity.ok(assignmentService.getAssignmentsByStatus(status, pageable));
    }


    @Operation(summary = "Get assignments for logged-in editor")
    @GetMapping("/my-assignments")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<Page<RecipeAssignmentResponse>> getMyAssignments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication auth) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("assignedDate").descending());
        return ResponseEntity.ok(assignmentService.getMyAssignments(auth.getName(), pageable));
    }

    @Operation(summary = "Update status of an assignment")
    @PutMapping("/assignments/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<RecipeAssignmentResponse> updateStatus(
            @PathVariable UUID id,
            @RequestParam String status,
            Authentication auth) {
        return ResponseEntity.ok(assignmentService.updateAssignmentStatus(id, status, auth.getName()));
    }

    @Operation(summary = "Stagiaire: Submit recipe assignment for validation")
    @PutMapping("/assignments/{id}/submit")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<RecipeAssignmentResponse> submitForValidation(
            @PathVariable UUID id,
            Authentication auth) {
        return ResponseEntity.ok(assignmentService.submitForValidation(id, auth.getName()));
    }

    @Operation(summary = "Admin: Validate recipe assignment")
    @PutMapping("/assignments/{id}/validate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RecipeAssignmentResponse> validateAssignment(
            @PathVariable UUID id,
            Authentication auth) {
        return ResponseEntity.ok(assignmentService.validateAssignment(id, auth.getName()));
    }

    @Operation(summary = "Admin: Reject recipe assignment with error categories and feedback comment")
    @PostMapping("/assignments/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RecipeAssignmentResponse> rejectAssignment(
            @PathVariable UUID id,
            @RequestBody RejectAssignmentRequest request,
            Authentication auth) {
        return ResponseEntity.ok(assignmentService.rejectAssignment(id, request, auth.getName()));
    }

    @Operation(summary = "Admin: Reassign recipe to a different editor")
    @PutMapping("/assignments/{id}/reassign")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RecipeAssignmentResponse> reassignAssignment(
            @PathVariable UUID id,
            @RequestParam UUID newUserId,
            Authentication auth) {
        return ResponseEntity.ok(assignmentService.reassignAssignment(id, newUserId, auth.getName()));
    }

    @Operation(summary = "Admin: Remove assignment")
    @DeleteMapping("/assignments/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeAssignment(
            @PathVariable UUID id,
            Authentication auth) {
        assignmentService.removeAssignment(id, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get audit history for an assignment")
    @GetMapping("/assignments/{id}/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<List<RecipeAssignmentHistoryResponse>> getAssignmentHistory(
            @PathVariable UUID id) {
        return ResponseEntity.ok(assignmentService.getAssignmentHistory(id));
    }
}
