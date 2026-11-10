package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.entity.Cookbook;
import com.cooked.backend.entity.Recipe;
import com.cooked.backend.entity.RecipeOrigin;
import com.cooked.backend.entity.Role;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.CookbookRepository;
import com.cooked.backend.repository.RecipeRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.ArchiveService;
import com.cooked.backend.service.NotificationService;
import com.cooked.backend.service.RecipeDeletionService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeDeletionServiceImpl implements RecipeDeletionService {

    static final String OWNER_DELETED_TYPE = "RECIPE_DELETED_BY_OWNER";

    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;
    private final CookbookRepository cookbookRepository;
    private final NotificationService notificationService;
    private final ArchiveService archiveService;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public MessageResponse deleteByOwner(UUID recipeId, String ownerEmail) {
        Recipe recipe = find(recipeId);
        User owner = recipe.getUser();
        if (owner == null || ownerEmail == null || !ownerEmail.equalsIgnoreCase(owner.getEmail())) {
            throw new BadRequestException("You do not have permission to delete this recipe.");
        }
        removeFromCookbooks(recipe);

        if (recipe.getOrigin() == RecipeOrigin.EXPLORE) {
            moveToTrash(recipe, ownerEmail);
            return new MessageResponse("Recipe deleted successfully.");
        }

        // A Scan / Import / Suggested / Manual recipe leaves the owner's library and becomes an Explore
        // recipe for the interns to rework. Hidden from the app until an admin validates it.
        String role = owner.getRole() == Role.CREATOR ? "CREATOR" : "CLIENT";
        recipe.setPreviousOrigin(recipe.getOrigin());
        recipe.setOrigin(RecipeOrigin.EXPLORE);
        recipe.setUser(null);
        recipe.setPinned(false);
        recipe.setExpiresAt(null);
        recipe.setPublic(false);
        recipe.setStatus(false);
        recipe.setDeleted(false);
        recipe.setLastModifiedBy(null); // back in the pool of recipes to assign
        recipe.setOwnerDeletedAt(LocalDateTime.now());
        recipe.setOwnerDeletedBy(ownerEmail);
        recipe.setOwnerDeletedRole(role);
        recipeRepository.save(recipe);
        notifyAdmins(recipe, owner, role);
        return new MessageResponse("Recipe deleted successfully.");
    }

    @Override
    @Transactional
    public void deleteFromBackoffice(UUID recipeId, String staffEmail) {
        Recipe recipe = find(recipeId);
        requireNoClientOwner(recipe);
        if (recipe.getOrigin() != RecipeOrigin.EXPLORE) {
            throw new BadRequestException("Seules les recettes Explore peuvent être supprimées depuis le backoffice. "
                    + "Une recette " + recipe.getOrigin() + " ne peut être supprimée que par son propriétaire.");
        }
        if (recipe.isDeleted()) return;
        moveToTrash(recipe, staffEmail);
    }

    @Override
    @Transactional
    public void restore(UUID recipeId, String staffEmail) {
        Recipe recipe = find(recipeId);
        requireNoClientOwner(recipe);
        if (recipe.getOrphanedAt() != null) {
            throw new BadRequestException("Recette d'un compte supprimé : choisissez « Garder » ou « Supprimer définitivement ».");
        }
        recipe.setDeleted(false);
        recipe.setDeletedAt(null);
        recipe.setDeletedBy(null);
        if (staffEmail != null) recipe.setLastModifiedBy(staffEmail);
        recipeRepository.save(recipe);
    }

    @Override
    @Transactional
    public void purge(UUID recipeId, String adminEmail) {
        Recipe recipe = find(recipeId);
        requireNoClientOwner(recipe);
        if (!recipe.isDeleted() && recipe.getOwnerDeletedAt() == null && recipe.getOrphanedAt() == null) {
            throw new BadRequestException("Seule une recette de l'archive peut être supprimée définitivement.");
        }
        String details = (recipe.getPreviousOrigin() != null ? recipe.getPreviousOrigin() : recipe.getOrigin())
                + (recipe.getOwnerDeletedBy() != null ? " · supprimée par " + recipe.getOwnerDeletedBy() : "");
        archiveService.record("RECIPE", recipe.getId(), recipe.getName(), details);

        UUID id = recipe.getId();
        removeFromCookbooks(recipe);
        sql("DELETE FROM recipe_assignment_error_categories WHERE assignment_id IN (SELECT id FROM recipe_assignments WHERE recipe_id = :id)", id);
        sql("DELETE FROM recipe_assignment_history WHERE assignment_id IN (SELECT id FROM recipe_assignments WHERE recipe_id = :id)", id);
        sql("DELETE FROM recipe_assignments WHERE recipe_id = :id", id);
        sql("DELETE FROM meal_plans WHERE recipe_id = :id", id);
        sql("UPDATE grocery_items SET recipe_id = NULL WHERE recipe_id = :id", id);
        sql("DELETE FROM recipe_image_matches WHERE recipe_id = :id", id);
        sql("DELETE FROM recipe_flags WHERE recipe_id = :id", id);
        sql("DELETE FROM cookbook_recipes WHERE recipe_id = :id", id);
        recipeRepository.delete(recipe);
        log.info("[RecipeDeletion] recipe {} deleted for good by {}", id, adminEmail);
    }

    @Override
    @Transactional
    public void keepOrphan(UUID recipeId, String adminEmail) {
        Recipe recipe = find(recipeId);
        if (recipe.getOrphanedAt() == null) {
            throw new BadRequestException("Cette recette n'appartient pas à un compte supprimé.");
        }
        if (recipe.getPreviousOrigin() == null) recipe.setPreviousOrigin(recipe.getOrigin());
        recipe.setOrigin(RecipeOrigin.EXPLORE);
        recipe.setOrphanedAt(null);
        recipe.setDeleted(false);
        recipe.setPublic(false);
        recipe.setStatus(false);
        recipe.setExpiresAt(null);
        recipe.setLastModifiedBy(null); // in the pool interns get assigned
        recipe.setOwnerDeletedAt(LocalDateTime.now()); // shown as "to rework" until validated
        if (recipe.getOwnerDeletedRole() == null) recipe.setOwnerDeletedRole("CLIENT");
        recipeRepository.save(recipe);
        log.info("[RecipeDeletion] orphan recipe {} kept for rework by {}", recipeId, adminEmail);
    }

    /** A recipe that still belongs to a client or creator can only be deleted by them. */
    private static void requireNoClientOwner(Recipe recipe) {
        User owner = recipe.getUser();
        if (owner != null && owner.getRole() != Role.ADMIN && owner.getRole() != Role.EDITOR) {
            throw new BadRequestException("Cette recette appartient à un " + (owner.getRole() == Role.CREATOR ? "creator" : "client")
                    + " : lui seul peut la supprimer.");
        }
    }

    private void moveToTrash(Recipe recipe, String email) {
        recipe.setDeleted(true);
        recipe.setDeletedAt(LocalDateTime.now());
        recipe.setDeletedBy(email);
        if (email != null) recipe.setLastModifiedBy(email);
        recipeRepository.save(recipe);
    }

    private void removeFromCookbooks(Recipe recipe) {
        if (recipe.getCookbooks() == null) return;
        for (Cookbook cb : new ArrayList<>(recipe.getCookbooks())) {
            cb.getRecipes().remove(recipe);
            cookbookRepository.save(cb);
        }
        recipe.getCookbooks().clear();
    }

    private void notifyAdmins(Recipe recipe, User owner, String role) {
        String who = "CREATOR".equals(role) ? "un creator" : "un client";
        String name = owner.getFirstname() != null && !owner.getFirstname().isBlank() ? owner.getFirstname() : owner.getEmail();
        for (User admin : userRepository.findAllByRole(Role.ADMIN)) {
            try {
                notificationService.createAndSendNotification(admin, owner, "Recette supprimée par " + who,
                        "« " + recipe.getName() + " » (" + recipe.getPreviousOrigin() + ") a été supprimée par " + name
                                + ". Elle est passée en Explore et attend d'être refaite (Archive).",
                        OWNER_DELETED_TYPE, recipe.getId(), null);
            } catch (RuntimeException e) {
                log.warn("[RecipeDeletion] admin notification failed: {}", e.getMessage());
            }
        }
    }

    private void sql(String statement, UUID id) {
        entityManager.createNativeQuery(statement).setParameter("id", id).executeUpdate();
    }

    private Recipe find(UUID id) {
        return recipeRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));
    }
}
