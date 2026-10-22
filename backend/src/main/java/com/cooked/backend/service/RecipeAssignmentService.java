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
    /** Most recipes one batch assignment or reassignment can move at once. */
    int MAX_BATCH = 500;

    RecipeStatsResponse getRecipeStats();
    List<RecipeAssignmentResponse> createAssignments(CreateAssignmentRequest request, String adminEmail);
    Page<RecipeAssignmentResponse> getAllAssignments(Pageable pageable);
    Page<RecipeAssignmentResponse> getAssignmentsByStatus(AssignmentStatus status, Pageable pageable);
    Page<RecipeAssignmentResponse> getMyAssignments(String editorEmail, Pageable pageable);

    /** Share of each correction reason over the last {@code days} days (Recipe guidelines). */
    java.util.List<com.cooked.backend.dto.response.CorrectionReasonShare> correctionReasons(int days);
    RecipeAssignmentResponse updateAssignmentStatus(UUID assignmentId, String newStatus, String userEmail);
    RecipeAssignmentResponse submitForValidation(UUID assignmentId, String editorEmail);
    RecipeAssignmentResponse validateAssignment(UUID assignmentId, String adminEmail);
    RecipeAssignmentResponse rejectAssignment(UUID assignmentId, RejectAssignmentRequest request, String adminEmail);
    RecipeAssignmentResponse reassignAssignment(UUID assignmentId, UUID newUserId, String adminEmail);
    /** Moves [count] unfinished recipes (not started first, then in progress) from one intern to another. */
    com.cooked.backend.dto.response.BatchReassignResponse reassignBatch(com.cooked.backend.dto.request.BatchReassignRequest request, String adminEmail);
    void removeAssignment(UUID assignmentId, String adminEmail);
    List<RecipeAssignmentHistoryResponse> getAssignmentHistory(UUID assignmentId);
    List<StagiaireLeaderboardResponse> getStagiairesLeaderboard();
    /** Leaderboard counting only assignments made in the last {@code days} days (null = all time). */
    List<StagiaireLeaderboardResponse> getStagiairesLeaderboard(Integer days);
    List<RecipeAssignmentResponse> assignBatchByCount(com.cooked.backend.dto.request.BatchAssignmentRequest request, String adminEmail);
    long getAvailableUnassignedCount();
    /** Same, limited to recipes from one source (null = all). */
    long getAvailableUnassignedCount(com.cooked.backend.entity.RecipeOrigin origin);

    /** includeAssigned also counts unmodified recipes another intern holds without having started. */
    long getAvailableUnassignedCount(com.cooked.backend.entity.RecipeOrigin origin, boolean includeAssigned);
}
