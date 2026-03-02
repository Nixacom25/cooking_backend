package com.cooked.backend.service;

import com.cooked.backend.dto.request.CreateMealPlanRequest;
import com.cooked.backend.dto.response.MealPlanResponse;
import com.cooked.backend.dto.response.MessageResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface MealPlanService {
    MealPlanResponse create(String userEmail, CreateMealPlanRequest request);

    List<MealPlanResponse> getMyMealPlans(String userEmail);

    List<MealPlanResponse> getMyMealPlansByDate(String userEmail, LocalDate date);

    MessageResponse delete(UUID id, String userEmail);
}
