package com.cooked.backend.repository;

import com.cooked.backend.entity.RecipeAssignment;
import com.cooked.backend.entity.AssignmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RecipeAssignmentRepository extends JpaRepository<RecipeAssignment, UUID> {
    
    Page<RecipeAssignment> findAllByAssignedToUserIdOrderByAssignedDateDesc(UUID userId, Pageable pageable);
    
    List<RecipeAssignment> findAllByAssignedToUserIdAndStatus(UUID userId, AssignmentStatus status);

    Page<RecipeAssignment> findAllByAssignedToUserIdAndStatusOrderByAssignedDateDesc(UUID userId, AssignmentStatus status, Pageable pageable);

    @Query("SELECT COUNT(ra) FROM RecipeAssignment ra WHERE ra.status IN ('NOT_STARTED', 'IN_PROGRESS')")
    long countActiveAssignments();

    @Query("SELECT ra FROM RecipeAssignment ra WHERE ra.recipe.id = :recipeId AND ra.status IN ('NOT_STARTED', 'IN_PROGRESS')")
    List<RecipeAssignment> findActiveAssignmentsByRecipeId(UUID recipeId);
}
