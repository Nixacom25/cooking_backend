package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.AdminDashboardResponse;
import com.cooked.backend.entity.RecipeOrigin;
import com.cooked.backend.entity.Role;
import com.cooked.backend.repository.CookbookRepository;
import com.cooked.backend.repository.RecipeRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final UserRepository userRepository;
    private final com.cooked.backend.service.monitoring.RequestMetrics requestMetrics;
    private final RecipeRepository recipeRepository;
    private final CookbookRepository cookbookRepository;

    @Override
    public AdminDashboardResponse getMetrics() {
        return AdminDashboardResponse.builder()
                .totalClients(userRepository.countByRole(Role.CLIENT))
                .totalRecipes(recipeRepository.count())
                .totalScans(recipeRepository.countByOrigin(RecipeOrigin.SCAN))
                .totalCookbooks(cookbookRepository.count())
                .build();
    }

    @Override
    public com.cooked.backend.dto.response.DashboardLiveResponse live() {
        java.time.LocalDateTime midnight = java.time.LocalDate.now().atStartOfDay();
        java.time.LocalDateTime yesterday = midnight.minusDays(1);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        return new com.cooked.backend.dto.response.DashboardLiveResponse(
                userRepository.countGroceryAddsBetween(midnight, now.plusMinutes(1)),
                userRepository.countGroceryAddsBetween(yesterday, midnight),
                userRepository.countMealPlansBetween(midnight, now.plusMinutes(1)),
                userRepository.countMealPlansBetween(yesterday, midnight),
                requestMetrics.scansInFlight(),
                requestMetrics.importsInFlight(),
                userRepository.countNewPayersSince(now.minusHours(1)));
    }
}
