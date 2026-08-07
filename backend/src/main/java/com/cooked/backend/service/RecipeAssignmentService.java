package com.cooked.backend.service;

import com.cooked.backend.dto.request.CreateAssignmentRequest;
import com.cooked.backend.dto.request.RejectAssignmentRequest;
import com.cooked.backend.dto.response.*;
import com.cooked.backend.entity.AssignmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface RecipeAssignmentService {
    RecipeStatsResponse getRecipeStats();
    List<RecipeAssignmentResponse> createAssignments(CreateAssignmentRequest request, String adminEmail);
    Page<RecipeAssignmentResponse> getAllAssignments(Pageable pageable);
    Page<RecipeAssignmentResponse> getAssignmentsByStatus(AssignmentStatus status, Pageable pageable);
    Page<RecipeAssignmentResponse> getMyAssignments(String editorEmail, Pageable pageable);
    RecipeAssignmentResponse updateAssignmentStatus(UUID assignmentId, String newStatus, String userEmail);
    RecipeAssignmentResponse submitForValidation(UUID assignmentId, String editorEmail);
    RecipeAssignmentResponse validateAssignment(UUID assignmentId, String adminEmail);
    RecipeAssignmentResponse rejectAssignment(UUID assignmentId, RejectAssignmentRequest request, String adminEmail);
    RecipeAssignmentResponse reassignAssignment(UUID assignmentId, UUID newUserId, String adminEmail);
    void removeAssignment(UUID assignmentId, String adminEmail);
    List<RecipeAssignmentHistoryResponse> getAssignmentHistory(UUID assignmentId);
    List<StagiaireLeaderboardResponse> getStagiairesLeaderboard();
}
