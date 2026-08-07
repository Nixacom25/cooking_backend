package com.cooked.backend.repository;

import com.cooked.backend.entity.RecipeAssignmentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RecipeAssignmentHistoryRepository extends JpaRepository<RecipeAssignmentHistory, UUID> {
    List<RecipeAssignmentHistory> findAllByRecipeAssignmentIdOrderByCreatedAtDesc(UUID assignmentId);
}
