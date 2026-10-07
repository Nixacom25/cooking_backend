package com.cooked.backend.security;

import com.cooked.backend.config.SecurityConfig;
import com.cooked.backend.controller.AdminAnalyticsController;
import com.cooked.backend.controller.AdminCostController;
import com.cooked.backend.controller.AdminTrendsController;
import com.cooked.backend.controller.AdminIntegrationController;
import com.cooked.backend.controller.AskCookedController;
import com.cooked.backend.controller.AdminAutomationController;
import com.cooked.backend.controller.AdminSettingsController;
import com.cooked.backend.controller.AdminTwoFactorController;
import com.cooked.backend.controller.AdminDashboardController;
import com.cooked.backend.controller.AdminRevenueController;
import com.cooked.backend.controller.DashboardController;
import com.cooked.backend.controller.RecipeDataController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards admin-only endpoints against regressions: each must require the
 * ADMIN role (class- or method-level), and none may be listed as public in
 * {@link SecurityConfig}.
 */
class AdminEndpointSecurityTest {

    private static final String ADMIN = "hasRole('ADMIN')";

    @Test
    void adminControllersRequireAdminOnTheClass() {
        for (Class<?> c : new Class<?>[] {RecipeDataController.class, AdminRevenueController.class, AdminAnalyticsController.class, AdminCostController.class, AdminTrendsController.class, AdminIntegrationController.class, AskCookedController.class, AdminAutomationController.class, AdminSettingsController.class, AdminTwoFactorController.class}) {
            PreAuthorize p = c.getAnnotation(PreAuthorize.class);
            assertNotNull(p, c.getSimpleName() + " must be @PreAuthorize");
            assertEquals(ADMIN, p.value(), c.getSimpleName());
        }
    }

    @Test
    void kpiAndDashboardRequireAdmin() throws Exception {
        assertMethodIsAdmin(DashboardController.class.getMethod("getGlobalKpis"));
        assertMethodIsAdmin(AdminDashboardController.class.getMethod("getDashboardMetrics"));
    }

    @Test
    void adminRoutesAreNotPublicInSecurityConfig() throws Exception {
        String config = Files.readString(Path.of("src/main/java/com/cooked/backend/config/SecurityConfig.java"));
        for (String route : new String[] {"/api/recipe-data", "/api/kpi", "/api/admin"}) {
            assertFalse(config.contains("\"" + route), route + " must not be in the permitAll list");
        }
    }

    private static void assertMethodIsAdmin(Method m) {
        PreAuthorize p = m.getAnnotation(PreAuthorize.class);
        assertNotNull(p, m.getDeclaringClass().getSimpleName() + "." + m.getName() + " must be @PreAuthorize");
        assertEquals(ADMIN, p.value());
    }
}
