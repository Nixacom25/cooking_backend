package com.cooked.backend.service;

import com.cooked.backend.dto.request.CreateAssignmentRequest;
import com.cooked.backend.dto.response.RecipeAssignmentResponse;
import com.cooked.backend.dto.response.RecipeStatsResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface RecipeAssignmentService {
    RecipeStatsResponse getRecipeStats();
    List<RecipeAssignmentResponse> createAssignments(CreateAssignmentRequest request, String adminEmail);
    Page<RecipeAssignmentResponse> getAllAssignments(Pageable pageable);
    Page<RecipeAssignmentResponse> getMyAssignments(String editorEmail, Pageable pageable);
    RecipeAssignmentResponse updateAssignmentStatus(UUID assignmentId, String newStatus, String editorEmail);
}
