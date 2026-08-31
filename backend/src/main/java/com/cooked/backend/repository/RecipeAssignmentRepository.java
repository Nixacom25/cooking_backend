package com.cooked.backend.repository;

import com.cooked.backend.entity.RecipeAssignment;
import com.cooked.backend.entity.AssignmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecipeAssignmentRepository extends JpaRepository<RecipeAssignment, UUID> {
    
    Page<RecipeAssignment> findAllByAssignedToUserIdOrderByAssignedDateDesc(UUID userId, Pageable pageable);
    
    List<RecipeAssignment> findAllByAssignedToUserIdAndStatus(UUID userId, AssignmentStatus status);

    Page<RecipeAssignment> findAllByAssignedToUserIdAndStatusOrderByAssignedDateDesc(UUID userId, AssignmentStatus status, Pageable pageable);

    Page<RecipeAssignment> findAllByStatusOrderByAssignedDateDesc(AssignmentStatus status, Pageable pageable);

    long countByStatus(AssignmentStatus status);

    long countByAssignedToUserId(UUID userId);

    long countByAssignedToUserIdAndStatus(UUID userId, AssignmentStatus status);

    @Query("SELECT COUNT(ra) FROM RecipeAssignment ra WHERE ra.status IN ('ASSIGNED', 'NOT_STARTED', 'IN_PROGRESS', 'NEEDS_CORRECTION')")
    long countActiveAssignments();

    @Query("SELECT ra FROM RecipeAssignment ra WHERE ra.recipe.id = :recipeId AND ra.status IN ('ASSIGNED', 'NOT_STARTED', 'IN_PROGRESS', 'SUBMITTED_FOR_VALIDATION', 'NEEDS_CORRECTION')")
    List<RecipeAssignment> findActiveAssignmentsByRecipeId(@Param("recipeId") UUID recipeId);

    Optional<RecipeAssignment> findFirstByRecipeIdOrderByAssignedDateDesc(UUID recipeId);

    @Query("SELECT COUNT(ra) FROM RecipeAssignment ra WHERE (ra.validatedDate >= :startOfDay OR ra.completedDate >= :startOfDay)")
    long countProcessedToday(@Param("startOfDay") LocalDateTime startOfDay);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE RecipeAssignment ra SET ra.recipe = :twinRecipe WHERE ra.recipe.id = :oldRecipeId")
    void repointRecipe(@Param("oldRecipeId") UUID oldRecipeId, @Param("twinRecipe") com.cooked.backend.entity.Recipe twinRecipe);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM RecipeAssignment ra WHERE ra.recipe.id = :recipeId")
    void deleteByRecipeId(@Param("recipeId") UUID recipeId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM RecipeAssignment ra WHERE ra.assignedToUser.id = :userId OR ra.assignedByUser.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
