package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.CreateAssignmentRequest;
import com.cooked.backend.dto.response.CreatorResponse;
import com.cooked.backend.dto.response.RecipeAssignmentResponse;
import com.cooked.backend.dto.response.RecipeStatsResponse;
import com.cooked.backend.entity.*;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.RecipeAssignmentRepository;
import com.cooked.backend.repository.RecipeRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.RecipeAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecipeAssignmentServiceImpl implements RecipeAssignmentService {

    private final RecipeAssignmentRepository assignmentRepository;
    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public RecipeStatsResponse getRecipeStats() {
        // Total (non-deleted)
        long total = recipeRepository.countByIsDeleted(false);
        // Deleted
        long deleted = recipeRepository.countByIsDeleted(true);
        // Modified = non-deleted + lastModifiedBy is not null
        long modified = recipeRepository.countByIsDeletedAndLastModifiedByIsNotNull(false);
        // Unmodified = total - modified
        long unmodified = total - modified;
        // Assigned = active assignments (not completed)
        long assigned = assignmentRepository.countActiveAssignments();
        // Unassigned = unmodified - assigned (recipes with no active assignment and not modified)
        long unassigned = Math.max(0, unmodified - assigned);

        return RecipeStatsResponse.builder()
                .totalRecipes(total)
                .deletedRecipes(deleted)
                .modifiedRecipes(modified)
                .unmodifiedRecipes(unmodified)
                .assignedRecipes(assigned)
                .unassignedRecipes(unassigned)
                .build();
    }

    @Override
    @Transactional
    public List<RecipeAssignmentResponse> createAssignments(CreateAssignmentRequest request, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        List<RecipeAssignmentResponse> results = new ArrayList<>();

        for (UUID recipeId : request.getRecipeIds()) {
            Recipe recipe = recipeRepository.findById(recipeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Recipe not found: " + recipeId));

            // Check if recipe is already actively assigned
            List<RecipeAssignment> existing = assignmentRepository.findActiveAssignmentsByRecipeId(recipeId);
            if (!existing.isEmpty()) {
                throw new BadRequestException("Recipe '" + recipe.getName() + "' is already assigned to someone.");
            }

            for (UUID userId : request.getUserIds()) {
                User editor = userRepository.findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

                AssignmentFrequency freq = request.getFrequency() != null
                        ? request.getFrequency()
                        : AssignmentFrequency.NONE;

                LocalDateTime dueDate = request.getDueDate();
                // If daily, set due date to end of today
                if (freq == AssignmentFrequency.DAILY) {
                    dueDate = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);
                } else if (freq == AssignmentFrequency.WEEKLY) {
                    dueDate = LocalDateTime.now().plusWeeks(1);
                }

                RecipeAssignment assignment = RecipeAssignment.builder()
                        .recipe(recipe)
                        .assignedToUser(editor)
                        .assignedByUser(admin)
                        .dueDate(dueDate)
                        .frequency(freq)
                        .status(AssignmentStatus.NOT_STARTED)
                        .build();

                RecipeAssignment saved = assignmentRepository.save(assignment);
                RecipeAssignmentResponse resp = mapToResponse(saved);
                results.add(resp);

                // Notify the specific editor in real time via WebSocket
                messagingTemplate.convertAndSend(
                        "/topic/assignments/user/" + userId,
                        resp
                );
            }
        }

        // Broadcast updated stats to all admin listeners
        broadcastStats();

        return results;
    }

    @Override
    public Page<RecipeAssignmentResponse> getAllAssignments(Pageable pageable) {
        return assignmentRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Override
    public Page<RecipeAssignmentResponse> getMyAssignments(String editorEmail, Pageable pageable) {
        User editor = userRepository.findByEmail(editorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return assignmentRepository.findAllByAssignedToUserIdOrderByAssignedDateDesc(editor.getId(), pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional
    public RecipeAssignmentResponse updateAssignmentStatus(UUID assignmentId, String newStatus, String editorEmail) {
        RecipeAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found"));

        // Verify editor owns this assignment
        if (!assignment.getAssignedToUser().getEmail().equals(editorEmail)) {
            throw new BadRequestException("You are not allowed to update this assignment.");
        }

        AssignmentStatus status;
        try {
            status = AssignmentStatus.valueOf(newStatus.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status: " + newStatus);
        }

        assignment.setStatus(status);
        if (status == AssignmentStatus.COMPLETED) {
            assignment.setCompletedDate(LocalDateTime.now());
        }

        RecipeAssignment saved = assignmentRepository.save(assignment);
        RecipeAssignmentResponse resp = mapToResponse(saved);

        // Broadcast updated stats to admins
        broadcastStats();

        return resp;
    }

    private void broadcastStats() {
        try {
            RecipeStatsResponse stats = getRecipeStats();
            messagingTemplate.convertAndSend("/topic/recipe-stats", stats);
        } catch (Exception e) {
            // Non-critical, don't fail the request
        }
    }

    private RecipeAssignmentResponse mapToResponse(RecipeAssignment a) {
        com.cooked.backend.dto.response.RecipeResponse recipeResp = recipeRepository
                .findById(a.getRecipe().getId())
                .map(this::buildMinimalRecipeResponse)
                .orElseGet(() -> buildMinimalRecipeResponse(a.getRecipe()));

        return RecipeAssignmentResponse.builder()
                .id(a.getId())
                .recipe(recipeResp)
                .assignedToUser(buildCreator(a.getAssignedToUser()))
                .assignedByUser(buildCreator(a.getAssignedByUser()))
                .assignedDate(a.getAssignedDate())
                .dueDate(a.getDueDate())
                .status(a.getStatus())
                .frequency(a.getFrequency())
                .completedDate(a.getCompletedDate())
                .build();
    }

    private com.cooked.backend.dto.response.RecipeResponse buildMinimalRecipeResponse(Recipe r) {
        return com.cooked.backend.dto.response.RecipeResponse.builder()
                .id(r.getId())
                .name(r.getName())
                .image(r.getImage())
                .status(r.getStatus())
                .lastModifiedBy(r.getLastModifiedBy())
                .build();
    }

    private CreatorResponse buildCreator(User u) {
        if (u == null) return null;
        return CreatorResponse.builder()
                .id(u.getId())
                .firstname(u.getFirstname())
                .lastname(u.getLastname())
                .photo(u.getPhoto())
                .build();
    }
}
