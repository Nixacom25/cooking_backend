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
        long total = recipeRepository.countLiveRecipes();
        long deleted = recipeRepository.countByIsDeleted(true);
        long unmodified = recipeRepository.countUnmodifiedRecipes();
        long modified = total - unmodified;
        long assigned = assignmentRepository.countActiveAssignments();
        // Same queries as the batch-assign modal, so the card and the modal always agree.
        long unassigned = recipeRepository.countUnassignedUnmodifiedRecipes();
        long reassignable = recipeRepository.countReassignableUnmodifiedRecipes();

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
                .unmodifiedAssignedRecipes(unmodified - unassigned)
                .unmodifiedReassignableRecipes(reassignable)
                .unmodifiedInReviewRecipes(unmodified - unassigned - reassignable)
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
                if (Boolean.TRUE.equals(request.getForceReassign())) {
                    // Cancel existing assignments before creating a new one
                    for (RecipeAssignment old : existing) {
                        recordHistory(old, admin, "REASSIGNED", old.getStatus(), AssignmentStatus.DELETED, null,
                                "Réassignation forcée par l'admin");
                        assignmentRepository.delete(old);
                    }
                } else {
                    throw new BadRequestException("Recipe '" + recipe.getName() + "' is already assigned. Use forceReassign=true to override.");
                }
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
    public long getAvailableUnassignedCount() {
        return recipeRepository.countUnassignedUnmodifiedRecipes();
    }

    @Override
    public long getAvailableUnassignedCount(RecipeOrigin origin) {
        return origin == null ? getAvailableUnassignedCount() : recipeRepository.countUnassignedUnmodifiedRecipesByOrigin(origin);
    }

    @Override
    public long getAvailableUnassignedCount(RecipeOrigin origin, boolean includeAssigned) {
        if (!includeAssigned) return getAvailableUnassignedCount(origin);
        return getAvailableUnassignedCount(origin) + (origin == null
                ? recipeRepository.countReassignableUnmodifiedRecipes()
                : recipeRepository.countReassignableUnmodifiedRecipesByOrigin(origin));
    }

    @Override
    @Transactional
    public List<RecipeAssignmentResponse> assignBatchByCount(com.cooked.backend.dto.request.BatchAssignmentRequest request, String adminEmail) {
        if (request.getCount() > MAX_BATCH) {
            throw new BadRequestException("Vous pouvez attribuer au maximum " + MAX_BATCH + " recettes à la fois.");
        }
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));
        User editor = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Stagiaire introuvable"));

        int requestedCount = request.getCount();
        if (requestedCount < 1) {
            throw new BadRequestException("Le nombre de recettes à attribuer doit être d'au moins 1.");
        }
        RecipeOrigin origin = request.getOrigin();
        var firstN = org.springframework.data.domain.PageRequest.of(0, requestedCount);
        List<Recipe> availableRecipes = origin == null
                ? recipeRepository.findUnassignedUnmodifiedRecipes(firstN)
                : recipeRepository.findUnassignedUnmodifiedRecipesByOrigin(origin, firstN);

        // Top up with not-started recipes other interns hold: one per recipe, oldest first.
        List<RecipeAssignment> toMove = new ArrayList<>();
        if (request.isIncludeAssigned() && availableRecipes.size() < requestedCount) {
            var rest = org.springframework.data.domain.PageRequest.of(0, requestedCount - availableRecipes.size());
            List<RecipeAssignment> candidates = origin == null
                    ? assignmentRepository.findReassignableUnmodified(editor.getId(), rest)
                    : assignmentRepository.findReassignableUnmodifiedByOrigin(editor.getId(), origin, rest);
            java.util.Set<UUID> seen = new java.util.HashSet<>();
            for (RecipeAssignment a : candidates) {
                if (seen.add(a.getRecipe().getId())) toMove.add(a);
            }
        }

        int found = availableRecipes.size() + toMove.size();
        if (found < requestedCount) {
            throw new BadRequestException("Impossible d'attribuer " + requestedCount + " recettes : seulement " + found + " recette(s) non modifiée(s) disponible(s)"
                    + (request.isIncludeAssigned() ? "." : ". Cochez « inclure les recettes déjà attribuées non commencées » pour en obtenir plus."));
        }

        String batchLabel = request.getBatchLabel() == null || request.getBatchLabel().isBlank() ? null : request.getBatchLabel().trim();
        List<RecipeAssignmentResponse> results = new ArrayList<>();

        for (RecipeAssignment a : toMove) {
            AssignmentStatus previous = a.getStatus();
            String previousEditor = a.getAssignedToUser() == null ? "?" : displayName(a.getAssignedToUser());
            a.setAssignedToUser(editor);
            a.setAssignedByUser(admin);
            a.setStatus(AssignmentStatus.ASSIGNED);
            if (batchLabel != null) a.setBatchLabel(batchLabel);
            if (request.getPriority() != null) a.setPriority(request.getPriority());
            RecipeAssignment saved = assignmentRepository.save(a);
            recordHistory(saved, admin, "REASSIGNED", previous, AssignmentStatus.ASSIGNED, null,
                    "Ré-assignée par lot de " + previousEditor + " à " + displayName(editor) + " (recette non modifiée)");
            RecipeAssignmentResponse resp = mapToResponse(saved);
            results.add(resp);
            messagingTemplate.convertAndSend("/topic/assignments/user/" + editor.getId(), resp);
        }

        for (Recipe recipe : availableRecipes) {
            RecipeAssignment assignment = RecipeAssignment.builder()
                    .recipe(recipe)
                    .assignedToUser(editor)
                    .assignedByUser(admin)
                    .frequency(AssignmentFrequency.NONE)
                    .status(AssignmentStatus.ASSIGNED)
                    .revisionCount(0)
                    .batchLabel(batchLabel)
                    .priority(request.getPriority())
                    .build();

            RecipeAssignment saved = assignmentRepository.save(assignment);
            recordHistory(saved, admin, "BATCH_ASSIGNED", null, AssignmentStatus.ASSIGNED, null, "Attribution automatique par lot (" + requestedCount + " recettes)");

            RecipeAssignmentResponse resp = mapToResponse(saved);
            results.add(resp);
            messagingTemplate.convertAndSend("/topic/assignments/user/" + editor.getId(), resp);
        }

        // One notification for the whole batch (up to MAX_BATCH recipes), not one per recipe.
        if (!results.isEmpty()) {
            notificationService.createAndSendNotification(editor, admin, "Nouvelles recettes attribuées",
                    results.size() + " recette(s) vous ont été attribuée(s).", "ASSIGNMENT", null, null);
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
        Page<RecipeAssignmentResponse> page = assignmentRepository.findAllByAssignedToUserIdOrderByAssignedDateDesc(editor.getId(), pageable)
                .map(this::mapToResponse);
        // "Validated by" for the intern's Completed screen, in one query.
        java.util.List<UUID> validated = page.getContent().stream()
                .filter(r -> r.getStatus() == com.cooked.backend.entity.AssignmentStatus.VALIDATED
                        || r.getStatus() == com.cooked.backend.entity.AssignmentStatus.COMPLETED)
                .map(RecipeAssignmentResponse::getId).toList();
        if (!validated.isEmpty()) {
            java.util.Map<UUID, String> by = new java.util.HashMap<>();
            for (RecipeAssignmentHistoryRepository.Validator v : historyRepository.findValidators(validated)) {
                String name = ((v.getFirstname() == null ? "" : v.getFirstname()) + " "
                        + (v.getLastname() == null || v.getLastname().isBlank() ? "" : v.getLastname().charAt(0) + ".")).trim();
                by.put(v.getAssignmentId(), name.isEmpty() ? v.getEmail() : name); // latest wins
            }
            page.getContent().forEach(r -> r.setValidatedBy(by.get(r.getId())));
        }
        return page;
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<com.cooked.backend.dto.response.CorrectionReasonShare> correctionReasons(int days) {
        int d = Math.max(1, Math.min(days, 365));
        java.util.List<RecipeAssignmentHistoryRepository.ReasonCount> rows =
                historyRepository.countCorrectionReasonsSince(java.time.LocalDateTime.now().minusDays(d));
        long total = rows.stream().mapToLong(r -> r.getTotal() == null ? 0 : r.getTotal()).sum();
        return rows.stream().filter(r -> r.getReason() != null)
                .map(r -> new com.cooked.backend.dto.response.CorrectionReasonShare(r.getReason(), r.getTotal(),
                        total == 0 ? 0 : Math.round(1000.0 * r.getTotal() / total) / 10.0))
                .toList();
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

        // Automatically activate & publish the recipe so it appears active in client mobile app
        Recipe recipe = assignment.getRecipe();
        if (recipe != null) {
            recipe.setPublic(true);
            recipeRepository.save(recipe);
        }

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

        if (newEditor.getRole() != Role.EDITOR) throw new BadRequestException("Le destinataire doit être un stagiaire.");

        User previousEditor = assignment.getAssignedToUser();
        AssignmentStatus previousStatus = assignment.getStatus();
        assignment.setAssignedToUser(newEditor);
        assignment.setAssignedByUser(admin);
        assignment.setStatus(AssignmentStatus.ASSIGNED);

        RecipeAssignment saved = assignmentRepository.save(assignment);
        recordHistory(saved, admin, "REASSIGNED", previousStatus, AssignmentStatus.ASSIGNED, null, "Re-assignée de " + previousEditor.getFirstname() + " à " + newEditor.getFirstname());

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

    static final java.util.List<AssignmentStatus> MOVABLE = java.util.List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.IN_PROGRESS);

    @Override
    @Transactional
    public com.cooked.backend.dto.response.BatchReassignResponse reassignBatch(com.cooked.backend.dto.request.BatchReassignRequest request, String adminEmail) {
        if (request.fromUserId().equals(request.toUserId())) throw new BadRequestException("Choisissez un autre stagiaire comme destinataire.");
        if (request.count() > MAX_BATCH) throw new BadRequestException("Vous pouvez réattribuer au maximum " + MAX_BATCH + " recettes à la fois.");
        User admin = userRepository.findByEmail(adminEmail).orElseThrow(() -> new ResourceNotFoundException("Admin not found"));
        User from = userRepository.findById(request.fromUserId()).orElseThrow(() -> new ResourceNotFoundException("Stagiaire introuvable"));
        User to = userRepository.findById(request.toUserId()).orElseThrow(() -> new ResourceNotFoundException("Stagiaire introuvable"));
        if (to.getRole() != Role.EDITOR) throw new BadRequestException("Le destinataire doit être un stagiaire.");

        List<RecipeAssignment> movable = assignmentRepository.findMovable(from.getId(), MOVABLE, org.springframework.data.domain.PageRequest.of(0, request.count()));
        if (movable.size() < request.count()) {
            throw new BadRequestException("Impossible de retirer " + request.count() + " recettes : " + from.getFirstname() + " n'en a que " + movable.size() + " non terminée(s).");
        }
        int notStarted = 0;
        for (RecipeAssignment a : movable) {
            AssignmentStatus previous = a.getStatus();
            if (previous == AssignmentStatus.ASSIGNED) notStarted++;
            a.setAssignedToUser(to);
            a.setAssignedByUser(admin);
            a.setStatus(AssignmentStatus.ASSIGNED);
            RecipeAssignment saved = assignmentRepository.save(a);
            recordHistory(saved, admin, "REASSIGNED", previous, AssignmentStatus.ASSIGNED, null,
                    "Ré-assignée par lot de " + from.getFirstname() + " à " + to.getFirstname());
        }
        notificationService.createAndSendNotification(to, admin, "Nouvelles recettes attribuées",
                movable.size() + " recette(s) vous ont été réattribuée(s).", "ASSIGNMENT", null, null);
        broadcastStats();
        return new com.cooked.backend.dto.response.BatchReassignResponse(movable.size(), notStarted, movable.size() - notStarted,
                displayName(from), displayName(to));
    }

    private static String displayName(User u) {
        String n = ((u.getFirstname() == null ? "" : u.getFirstname()) + " " + (u.getLastname() == null ? "" : u.getLastname())).trim();
        return n.isEmpty() ? u.getEmail() : n;
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

    /** Per user: [sum hours to submit, count, sum hours to validate, count]. */
    static java.util.Map<java.util.UUID, double[]> timingByUser(List<RecipeAssignmentRepository.Timing> rows) {
        java.util.Map<java.util.UUID, double[]> out = new java.util.HashMap<>();
        for (var t : rows) {
            double[] acc = out.computeIfAbsent(t.getUserId(), k -> new double[4]);
            if (t.getAssignedDate() != null && !t.getSubmittedDate().isBefore(t.getAssignedDate())) {
                acc[0] += java.time.Duration.between(t.getAssignedDate(), t.getSubmittedDate()).toMinutes() / 60.0;
                acc[1]++;
            }
            if (t.getValidatedDate() != null && !t.getValidatedDate().isBefore(t.getSubmittedDate())) {
                acc[2] += java.time.Duration.between(t.getSubmittedDate(), t.getValidatedDate()).toMinutes() / 60.0;
                acc[3]++;
            }
        }
        return out;
    }

    /** Average from timingByUser at [offset] (0 = submit, 2 = validate), rounded to 0.1 h; null without data. */
    static Double avgHours(double[] acc, int offset) {
        if (acc == null || acc[offset + 1] == 0) return null;
        return Math.round(acc[offset] / acc[offset + 1] * 10.0) / 10.0;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StagiaireLeaderboardResponse> getStagiairesLeaderboard() {
        return getStagiairesLeaderboard(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StagiaireLeaderboardResponse> getStagiairesLeaderboard(Integer days) {
        List<User> editors = userRepository.findAllByRole(Role.EDITOR);

        // One aggregate query for every intern and status (was 9 count queries per intern).
        var rows = days == null
                ? assignmentRepository.countByUserAndStatus()
                : assignmentRepository.countByUserAndStatusSince(LocalDateTime.now().minusDays(Math.min(Math.max(days, 1), 3650)));
        java.util.Map<java.util.UUID, java.util.Map<AssignmentStatus, Long>> counts = new java.util.HashMap<>();
        for (var c : rows) {
            counts.computeIfAbsent(c.getUserId(), k -> new java.util.EnumMap<>(AssignmentStatus.class))
                    .merge(c.getStatus(), c.getTotal() == null ? 0L : c.getTotal(), Long::sum);
        }

        java.util.Map<java.util.UUID, double[]> times = timingByUser(assignmentRepository.timingsSince(
                days == null ? LocalDateTime.of(2000, 1, 1, 0, 0) : LocalDateTime.now().minusDays(Math.min(Math.max(days, 1), 3650))));

        List<StagiaireLeaderboardResponse> leaderboard = new ArrayList<>();

        for (User ed : editors) {
            java.util.Map<AssignmentStatus, Long> byStatus = counts.getOrDefault(ed.getId(), java.util.Map.of());
            java.util.function.ToLongFunction<AssignmentStatus> n = s -> byStatus.getOrDefault(s, 0L);
            long assigned = byStatus.values().stream().mapToLong(Long::longValue).sum();
            long validated = n.applyAsLong(AssignmentStatus.VALIDATED) + n.applyAsLong(AssignmentStatus.COMPLETED);
            long deleted = n.applyAsLong(AssignmentStatus.DELETED);
            long pending = n.applyAsLong(AssignmentStatus.SUBMITTED_FOR_VALIDATION);
            long returned = n.applyAsLong(AssignmentStatus.NEEDS_CORRECTION);
            long remaining = n.applyAsLong(AssignmentStatus.ASSIGNED) + n.applyAsLong(AssignmentStatus.IN_PROGRESS);

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
                    .avgHoursToSubmit(avgHours(times.get(ed.getId()), 0))
                    .avgHoursToValidate(avgHours(times.get(ed.getId()), 2))
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
                .batchLabel(a.getBatchLabel())
                .priority(a.getPriority())
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
        if (r == null) return null;
        
        java.util.List<com.cooked.backend.dto.response.RecipeIngredientResponse> ingResponses = r.getRecipeIngredients() == null ? java.util.Collections.emptyList()
                : r.getRecipeIngredients().stream()
                        .filter(ri -> ri.getIngredient() != null)
                        .map(ri -> com.cooked.backend.dto.response.RecipeIngredientResponse.builder()
                                .id(ri.getIngredient().getId())
                                .name(ri.getIngredient().getName())
                                .icon(ri.getIngredient().getIcon())
                                .price(ri.getIngredient().getPrice())
                                .quantity(ri.getQuantity())
                                .build()).collect(java.util.stream.Collectors.toList());

        return com.cooked.backend.dto.response.RecipeResponse.builder()
                .id(r.getId())
                .name(r.getName())
                .image(r.getImage())
                .prepTime(r.getPrepTime())
                .cookTime(r.getCookTime())
                .kcal(r.getKcal())
                .servings(r.getServings())
                .tips(r.getTips())
                .cuisine(r.getCuisine() != null ? r.getCuisine().getName() : null)
                .categories(r.getCategories() != null ? r.getCategories().stream().map(c -> c.getName()).collect(java.util.stream.Collectors.toList()) : new java.util.ArrayList<>())
                .ingredients(ingResponses)
                .steps(r.getSteps() != null ? new java.util.ArrayList<>(r.getSteps()) : new java.util.ArrayList<>())
                .equipment(r.getEquipment() != null ? new java.util.ArrayList<>(r.getEquipment()) : new java.util.ArrayList<>())
                .totalPrice(r.getTotalPrice())
                .ingredientsCount(r.getIngredientsCount())
                .status(r.getStatus())
                .isDeleted(r.isDeleted())
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
