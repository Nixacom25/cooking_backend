package com.cooked.backend.repository;

import com.cooked.backend.entity.RecipeAssignmentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RecipeAssignmentHistoryRepository extends JpaRepository<RecipeAssignmentHistory, UUID> {
    List<RecipeAssignmentHistory> findAllByRecipeAssignmentIdOrderByCreatedAtDesc(UUID assignmentId);

    /** Who validated each assignment (the VALIDATED history entries of these assignments, oldest first). */
    interface Validator {
        UUID getAssignmentId();
        String getFirstname();
        String getLastname();
        String getEmail();
    }

    @org.springframework.data.jpa.repository.Query("select h.recipeAssignment.id as assignmentId, a.firstname as firstname, "
            + "a.lastname as lastname, a.email as email from RecipeAssignmentHistory h join h.actor a "
            + "where h.recipeAssignment.id in :ids and h.newStatus = com.cooked.backend.entity.AssignmentStatus.VALIDATED "
            + "order by h.createdAt")
    List<Validator> findValidators(@org.springframework.data.repository.query.Param("ids") java.util.Collection<UUID> ids);

    /** How often each correction reason was used when sending recipes back since [since]. */
    interface ReasonCount {
        String getReason();
        Long getTotal();
    }

    @org.springframework.data.jpa.repository.Query("select c as reason, count(h) as total from RecipeAssignmentHistory h "
            + "join h.errorCategories c where h.newStatus = com.cooked.backend.entity.AssignmentStatus.NEEDS_CORRECTION "
            + "and h.createdAt >= :since group by c order by count(h) desc")
    List<ReasonCount> countCorrectionReasonsSince(@org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since);
}
