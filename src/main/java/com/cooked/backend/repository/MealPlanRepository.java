package com.cooked.backend.repository;

import com.cooked.backend.entity.MealPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface MealPlanRepository extends JpaRepository<MealPlan, UUID> {
    List<MealPlan> findAllByUserId(UUID userId);

    List<MealPlan> findAllByUserIdAndPlannedDate(UUID userId, LocalDate plannedDate);
}
