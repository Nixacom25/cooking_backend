package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.CreateAssignmentRequest;
import com.cooked.backend.dto.request.RejectAssignmentRequest;
import com.cooked.backend.dto.response.*;
import com.cooked.backend.entity.*;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.*;
import com.cooked.backend.service.NotificationService;
import com.cooked.backend.service.RecipeAssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeAssignmentServiceImpl implements RecipeAssignmentService {

    private final RecipeAssignmentRepository assignmentRepository;
    private final RecipeAssignmentHistoryRepository historyRepository;
    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public RecipeStatsResponse getRecipeStats() {
        long total = recipeRepository.countByIsDeleted(false);
        long deleted = recipeRepository.countByIsDeleted(true);
        long modified = recipeRepository.countByIsDeletedAndLastModifiedByIsNotNull(false);
        long unmodified = total - modified;
        long assigned = assignmentRepository.countActiveAssignments();
        long unassigned = Math.max(0, unmodified - assigned);

        long pendingValidation = assignmentRepository.countByStatus(AssignmentStatus.SUBMITTED_FOR_VALIDATION);
        long validated = assignmentRepository.countByStatus(AssignmentStatus.VALIDATED) + assignmentRepository.countByStatus(AssignmentStatus.COMPLETED);
        long needsCorrection = assignmentRepository.countByStatus(AssignmentStatus.NEEDS_CORRECTION);
        long inProgress = assignmentRepository.countByStatus(AssignmentStatus.IN_PROGRESS);

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        long processedToday = assignmentRepository.countProcessedToday(startOfDay);

        return RecipeStatsResponse.builder()
                .totalRecipes(total)
                .deletedRecipes(deleted)
                .modifiedRecipes(modified)
                .unmodifiedRecipes(unmodified)
                .assignedRecipes(assigned)
                .unassignedRecipes(unassigned)
                .pendingValidationRecipes(pendingValidation)
                .validatedRecipes(validated)
                .needsCorrectionRecipes(needsCorrection)
                .inProgressRecipes(inProgress)
                .processedToday(processedToday)
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

            List<RecipeAssignment> existing = assignmentRepository.findActiveAssignmentsByRecipeId(recipeId);
            if (!existing.isEmpty()) {
                throw new BadRequestException("Recipe '" + recipe.getName() + "' is already assigned.");
            }

            for (UUID userId : request.getUserIds()) {
                User editor = userRepository.findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

                AssignmentFrequency freq = request.getFrequency() != null ? request.getFrequency() : AssignmentFrequency.NONE;
                LocalDateTime dueDate = request.getDueDate();
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
                        .status(AssignmentStatus.ASSIGNED)
                        .revisionCount(0)
                        .build();

                RecipeAssignment saved = assignmentRepository.save(assignment);

                // Audit Log
                recordHistory(saved, admin, "ASSIGNED", null, AssignmentStatus.ASSIGNED, null, "Assignation initiale");

                // In-App & WS Notification for Editor
                notificationService.createAndSendNotification(
                        editor,
                        admin,
                        "Nouvelle recette assignée",
                        "Vous avez reçu la recette : " + recipe.getName(),
                        "ASSIGNMENT",
                        recipe.getId(),
                        saved.getId()
                );

                RecipeAssignmentResponse resp = mapToResponse(saved);
                results.add(resp);

                messagingTemplate.convertAndSend("/topic/assignments/user/" + userId, resp);
            }
        }

        broadcastStats();
        return results;
    }

    @Override
    public Page<RecipeAssignmentResponse> getAllAssignments(Pageable pageable) {
        return assignmentRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Override
    public Page<RecipeAssignmentResponse> getAssignmentsByStatus(AssignmentStatus status, Pageable pageable) {
        return assignmentRepository.findAllByStatusOrderByAssignedDateDesc(status, pageable).map(this::mapToResponse);
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
    public RecipeAssignmentResponse updateAssignmentStatus(UUID assignmentId, String newStatus, String userEmail) {
        RecipeAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found"));
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        AssignmentStatus status;
        try {
            status = AssignmentStatus.valueOf(newStatus.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status: " + newStatus);
        }

        AssignmentStatus prevStatus = assignment.getStatus();
        assignment.setStatus(status);
        if (status == AssignmentStatus.COMPLETED || status == AssignmentStatus.VALIDATED) {
            assignment.setCompletedDate(LocalDateTime.now());
            if (assignment.getValidatedDate() == null) assignment.setValidatedDate(LocalDateTime.now());
        }

        RecipeAssignment saved = assignmentRepository.save(assignment);
        recordHistory(saved, user, "STATUS_UPDATE", prevStatus, status, null, "Mise à jour du statut à " + status);
        broadcastStats();
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public RecipeAssignmentResponse submitForValidation(UUID assignmentId, String editorEmail) {
        RecipeAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found"));
        User editor = userRepository.findByEmail(editorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!assignment.getAssignedToUser().getId().equals(editor.getId())) {
            throw new BadRequestException("Seul l'éditeur assigné peut soumettre cette recette.");
        }

        AssignmentStatus prevStatus = assignment.getStatus();
        assignment.setStatus(AssignmentStatus.SUBMITTED_FOR_VALIDATION);
        assignment.setSubmittedDate(LocalDateTime.now());

        RecipeAssignment saved = assignmentRepository.save(assignment);
        recordHistory(saved, editor, "SUBMITTED_FOR_VALIDATION", prevStatus, AssignmentStatus.SUBMITTED_FOR_VALIDATION, null, "Recette soumise pour validation par le stagiaire");

        // Notify Admin
        notificationService.createAndSendNotification(
                assignment.getAssignedByUser(),
                editor,
                "Recette à valider",
                editor.getFirstname() + " a soumis la recette : " + assignment.getRecipe().getName(),
                "VALIDATION_SUBMITTED",
                assignment.getRecipe().getId(),
                saved.getId()
        );

        RecipeAssignmentResponse resp = mapToResponse(saved);
        messagingTemplate.convertAndSend("/topic/admin/validation-queue", resp);
        broadcastStats();
        return resp;
    }

    @Override
    @Transactional
    public RecipeAssignmentResponse validateAssignment(UUID assignmentId, String adminEmail) {
        RecipeAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found"));
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        AssignmentStatus prevStatus = assignment.getStatus();
        assignment.setStatus(AssignmentStatus.VALIDATED);
        assignment.setValidatedDate(LocalDateTime.now());
        assignment.setCompletedDate(LocalDateTime.now());
        assignment.setErrorCategories(new ArrayList<>());
        assignment.setFeedbackComment(null);

        RecipeAssignment saved = assignmentRepository.save(assignment);
        recordHistory(saved, admin, "VALIDATED", prevStatus, AssignmentStatus.VALIDATED, null, "Recette validée par l'administrateur");

        // Notify Editor
        notificationService.createAndSendNotification(
                assignment.getAssignedToUser(),
                admin,
                "Recette validée 🎉",
                "Félicitations ! Votre recette '" + assignment.getRecipe().getName() + "' a été validée.",
                "RECIPE_VALIDATED",
                assignment.getRecipe().getId(),
                saved.getId()
        );

        RecipeAssignmentResponse resp = mapToResponse(saved);
        messagingTemplate.convertAndSend("/topic/assignments/user/" + assignment.getAssignedToUser().getId(), resp);
        broadcastStats();
        return resp;
    }

    @Override
    @Transactional
    public RecipeAssignmentResponse rejectAssignment(UUID assignmentId, RejectAssignmentRequest request, String adminEmail) {
        RecipeAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found"));
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        AssignmentStatus prevStatus = assignment.getStatus();
        assignment.setStatus(AssignmentStatus.NEEDS_CORRECTION);
        assignment.setErrorCategories(request.getErrorCategories() != null ? request.getErrorCategories() : new ArrayList<>());
        assignment.setFeedbackComment(request.getFeedbackComment());
        assignment.setRevisionCount((assignment.getRevisionCount() == null ? 0 : assignment.getRevisionCount()) + 1);

        RecipeAssignment saved = assignmentRepository.save(assignment);
        recordHistory(saved, admin, "REJECTED_FOR_CORRECTION", prevStatus, AssignmentStatus.NEEDS_CORRECTION, request.getErrorCategories(), request.getFeedbackComment());

        // Notify Editor
        notificationService.createAndSendNotification(
                assignment.getAssignedToUser(),
                admin,
                "Recette à corriger ⚠️",
                "Des corrections ont été demandées sur la recette '" + assignment.getRecipe().getName() + "'.",
                "CORRECTION_REQUESTED",
                assignment.getRecipe().getId(),
                saved.getId()
        );

        RecipeAssignmentResponse resp = mapToResponse(saved);
        messagingTemplate.convertAndSend("/topic/assignments/user/" + assignment.getAssignedToUser().getId(), resp);
        broadcastStats();
        return resp;
    }

    @Override
    @Transactional
    public RecipeAssignmentResponse reassignAssignment(UUID assignmentId, UUID newUserId, String adminEmail) {
        RecipeAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found"));
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));
        User newEditor = userRepository.findById(newUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + newUserId));

        User previousEditor = assignment.getAssignedToUser();
        assignment.setAssignedToUser(newEditor);
        assignment.setAssignedByUser(admin);
        assignment.setStatus(AssignmentStatus.ASSIGNED);

        RecipeAssignment saved = assignmentRepository.save(assignment);
        recordHistory(saved, admin, "REASSIGNED", assignment.getStatus(), AssignmentStatus.ASSIGNED, null, "Re-assignée de " + previousEditor.getFirstname() + " à " + newEditor.getFirstname());

        notificationService.createAndSendNotification(
                newEditor,
                admin,
                "Recette réassignée",
                "La recette '" + assignment.getRecipe().getName() + "' vous a été attribuée.",
                "ASSIGNMENT",
                assignment.getRecipe().getId(),
                saved.getId()
        );

        RecipeAssignmentResponse resp = mapToResponse(saved);
        messagingTemplate.convertAndSend("/topic/assignments/user/" + newUserId, resp);
        broadcastStats();
        return resp;
    }

    @Override
    @Transactional
    public void removeAssignment(UUID assignmentId, String adminEmail) {
        RecipeAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found"));
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

        recordHistory(assignment, admin, "REMOVED", assignment.getStatus(), AssignmentStatus.UNASSIGNED, null, "Assignation supprimée");
        assignmentRepository.delete(assignment);
        broadcastStats();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecipeAssignmentHistoryResponse> getAssignmentHistory(UUID assignmentId) {
        return historyRepository.findAllByRecipeAssignmentIdOrderByCreatedAtDesc(assignmentId)
                .stream()
                .map(this::mapHistoryToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StagiaireLeaderboardResponse> getStagiairesLeaderboard() {
        List<User> editors = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.EDITOR)
                .toList();

        List<StagiaireLeaderboardResponse> leaderboard = new ArrayList<>();

        for (User ed : editors) {
            long assigned = assignmentRepository.countByAssignedToUserId(ed.getId());
            long validated = assignmentRepository.countByAssignedToUserIdAndStatus(ed.getId(), AssignmentStatus.VALIDATED)
                    + assignmentRepository.countByAssignedToUserIdAndStatus(ed.getId(), AssignmentStatus.COMPLETED);
            long deleted = assignmentRepository.countByAssignedToUserIdAndStatus(ed.getId(), AssignmentStatus.DELETED);
            long pending = assignmentRepository.countByAssignedToUserIdAndStatus(ed.getId(), AssignmentStatus.SUBMITTED_FOR_VALIDATION);
            long returned = assignmentRepository.countByAssignedToUserIdAndStatus(ed.getId(), AssignmentStatus.NEEDS_CORRECTION);
            long remaining = assignmentRepository.countByAssignedToUserIdAndStatus(ed.getId(), AssignmentStatus.ASSIGNED)
                    + assignmentRepository.countByAssignedToUserIdAndStatus(ed.getId(), AssignmentStatus.IN_PROGRESS);

            double validationRate = assigned > 0 ? (double) validated / assigned * 100.0 : 0.0;
            double progressPercentage = assigned > 0 ? (double) (validated + deleted) / assigned * 100.0 : 0.0;

            leaderboard.add(StagiaireLeaderboardResponse.builder()
                    .userId(ed.getId())
                    .firstname(ed.getFirstname())
                    .lastname(ed.getLastname())
                    .email(ed.getEmail())
                    .photo(ed.getPhoto())
                    .totalAssigned(assigned)
                    .totalValidated(validated)
                    .totalDeleted(deleted)
                    .totalPendingValidation(pending)
                    .totalReturnedForCorrection(returned)
                    .totalRemaining(remaining)
                    .validationRate(Math.round(validationRate * 10.0) / 10.0)
                    .progressPercentage(Math.round(progressPercentage * 10.0) / 10.0)
                    .build());
        }

        // Sort by totalValidated desc, then validationRate desc
        leaderboard.sort(Comparator.comparingLong(StagiaireLeaderboardResponse::getTotalValidated).reversed()
                .thenComparing(Comparator.comparingDouble(StagiaireLeaderboardResponse::getValidationRate).reversed()));

        // Assign ranks and badges
        for (int i = 0; i < leaderboard.size(); i++) {
            StagiaireLeaderboardResponse item = leaderboard.get(i);
            item.setRank(i + 1);
            if (i == 0) item.setBadge("🥇");
            else if (i == 1) item.setBadge("🥈");
            else if (i == 2) item.setBadge("🥉");
            else item.setBadge("");
        }

        return leaderboard;
    }

    private void recordHistory(RecipeAssignment assignment, User actor, String action, AssignmentStatus prevStatus, AssignmentStatus newStatus, List<String> errorCategories, String comment) {
        RecipeAssignmentHistory history = RecipeAssignmentHistory.builder()
                .recipeAssignment(assignment)
                .actor(actor)
                .action(action)
                .previousStatus(prevStatus)
                .newStatus(newStatus)
                .errorCategories(errorCategories != null ? errorCategories : new ArrayList<>())
                .comment(comment)
                .build();
        historyRepository.save(history);
    }

    private void broadcastStats() {
        try {
            RecipeStatsResponse stats = getRecipeStats();
            messagingTemplate.convertAndSend("/topic/recipe-stats", stats);
        } catch (Exception e) {
            log.error("Error broadcasting stats: {}", e.getMessage());
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
                .submittedDate(a.getSubmittedDate())
                .validatedDate(a.getValidatedDate())
                .errorCategories(a.getErrorCategories())
                .feedbackComment(a.getFeedbackComment())
                .revisionCount(a.getRevisionCount())
                .build();
    }

    private RecipeAssignmentHistoryResponse mapHistoryToResponse(RecipeAssignmentHistory h) {
        return RecipeAssignmentHistoryResponse.builder()
                .id(h.getId())
                .actor(buildCreator(h.getActor()))
                .action(h.getAction())
                .previousStatus(h.getPreviousStatus())
                .newStatus(h.getNewStatus())
                .errorCategories(h.getErrorCategories())
                .comment(h.getComment())
                .createdAt(h.getCreatedAt())
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
                .email(u.getEmail())
                .photo(u.getPhoto())
                .build();
    }
}
