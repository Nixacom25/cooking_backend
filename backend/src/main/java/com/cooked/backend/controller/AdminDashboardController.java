package com.cooked.backend.controller;

import com.cooked.backend.dto.response.AdminDashboardResponse;
import com.cooked.backend.entity.Role;
import com.cooked.backend.entity.RecipeOrigin;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.repository.RecipeRepository;
import com.cooked.backend.repository.CookbookRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "Admin Dashboard", description = "Dashboard metrics for administrators")
public class AdminDashboardController {

    private final UserRepository userRepository;
    private final RecipeRepository recipeRepository;
    private final CookbookRepository cookbookRepository;

    @Operation(summary = "Get global dashboard metrics")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminDashboardResponse> getDashboardMetrics() {
        long totalClients = userRepository.countByRole(Role.CLIENT);
        long totalRecipes = recipeRepository.count();
        long totalScans = recipeRepository.countByOrigin(RecipeOrigin.SCAN);
        long totalCookbooks = cookbookRepository.count();

        AdminDashboardResponse response = AdminDashboardResponse.builder()
                .totalClients(totalClients)
                .totalRecipes(totalRecipes)
                .totalScans(totalScans)
                .totalCookbooks(totalCookbooks)
                .build();

        return ResponseEntity.ok(response);
    }
}
