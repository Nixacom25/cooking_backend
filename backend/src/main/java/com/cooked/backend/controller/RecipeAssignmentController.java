package com.cooked.backend.controller;

import com.cooked.backend.dto.request.CreateAssignmentRequest;
import com.cooked.backend.dto.response.RecipeAssignmentResponse;
import com.cooked.backend.dto.response.RecipeStatsResponse;
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
@Tag(name = "Recipe Assignments", description = "Admin recipe stats & assignment management")
@SecurityRequirement(name = "bearerAuth")
public class RecipeAssignmentController {

    private final RecipeAssignmentService assignmentService;

    // ─── ADMIN: Stats ──────────────────────────────────────────────────────────

    @Operation(summary = "Get recipe statistics dashboard")
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RecipeStatsResponse> getStats() {
        return ResponseEntity.ok(assignmentService.getRecipeStats());
    }

    // ─── ADMIN: Create Assignments ─────────────────────────────────────────────

    @Operation(summary = "Assign recipes to editors")
    @PostMapping("/assignments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<RecipeAssignmentResponse>> createAssignments(
            @RequestBody CreateAssignmentRequest request,
            Authentication auth) {
        return ResponseEntity.ok(assignmentService.createAssignments(request, auth.getName()));
    }

    // ─── ADMIN: View all assignments ───────────────────────────────────────────

    @Operation(summary = "Get all recipe assignments (admin view)")
    @GetMapping("/assignments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<RecipeAssignmentResponse>> getAllAssignments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("assignedDate").descending());
        return ResponseEntity.ok(assignmentService.getAllAssignments(pageable));
    }

    // ─── EDITOR: View my assignments ───────────────────────────────────────────

    @Operation(summary = "Get assignments for logged-in editor")
    @GetMapping("/my-assignments")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<Page<RecipeAssignmentResponse>> getMyAssignments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("assignedDate").descending());
        return ResponseEntity.ok(assignmentService.getMyAssignments(auth.getName(), pageable));
    }

    // ─── EDITOR: Update assignment status ──────────────────────────────────────

    @Operation(summary = "Update status of an assignment")
    @PutMapping("/assignments/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<RecipeAssignmentResponse> updateStatus(
            @PathVariable UUID id,
            @RequestParam String status,
            Authentication auth) {
        return ResponseEntity.ok(assignmentService.updateAssignmentStatus(id, status, auth.getName()));
    }
}
