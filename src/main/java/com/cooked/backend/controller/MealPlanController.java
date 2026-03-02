package com.cooked.backend.controller;

import com.cooked.backend.dto.request.CreateMealPlanRequest;
import com.cooked.backend.dto.response.MealPlanResponse;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.service.MealPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/meal-plans")
@RequiredArgsConstructor
@Tag(name = "Meal Plan", description = "Endpoints for scheduling meals")
@SecurityRequirement(name = "bearerAuth")
public class MealPlanController {

    private final MealPlanService mealPlanService;

    @Operation(summary = "Schedule a new recipe for a specific date and time")
    @PostMapping
    public ResponseEntity<MealPlanResponse> create(Authentication auth,
            @Valid @RequestBody CreateMealPlanRequest request) {
        return ResponseEntity.ok(mealPlanService.create(auth.getName(), request));
    }

    @Operation(summary = "Get all my scheduled meal plans")
    @GetMapping
    public ResponseEntity<List<MealPlanResponse>> getMyMealPlans(Authentication auth) {
        return ResponseEntity.ok(mealPlanService.getMyMealPlans(auth.getName()));
    }

    @Operation(summary = "Get my meal plans for a specific date")
    @GetMapping("/date/{date}")
    public ResponseEntity<List<MealPlanResponse>> getMyMealPlansByDate(
            Authentication auth,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(mealPlanService.getMyMealPlansByDate(auth.getName(), date));
    }

    @Operation(summary = "Delete a scheduled meal plan")
    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> delete(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(mealPlanService.delete(id, auth.getName()));
    }
}
