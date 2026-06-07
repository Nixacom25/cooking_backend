package com.cooked.backend.repository;

import com.cooked.backend.entity.MealPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface MealPlanRepository extends JpaRepository<MealPlan, UUID> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"recipe"})
    List<MealPlan> findAllByUserId(UUID userId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"recipe"})
    List<MealPlan> findAllByUserIdAndPlannedDate(UUID userId, LocalDate plannedDate);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("UPDATE MealPlan mp SET mp.recipe = :twin WHERE mp.recipe = :oldRecipe")
    void repointRecipe(@org.springframework.data.repository.query.Param("oldRecipe") com.cooked.backend.entity.Recipe oldRecipe, 
                       @org.springframework.data.repository.query.Param("twin") com.cooked.backend.entity.Recipe twin);
}
