package com.cooked.backend.security;

import com.cooked.backend.entity.SubscriptionStatus;
import com.cooked.backend.entity.User;
import com.cooked.backend.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
public class SubscriptionRequiredFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionRequiredFilter.class);

    private final UserRepository userRepository;

    public SubscriptionRequiredFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Skip subscription check for public endpoints
        String path = request.getRequestURI();
        if (isPublicEndpoint(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Check if user is authenticated
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() ||
                !(authentication instanceof UsernamePasswordAuthenticationToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Get user email from authentication
        String email = authentication.getName();
        if (email == null || email.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Check subscription status
        try {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (!hasActiveSubscription(user)) {
                log.warn("Access denied for user {} - No active subscription (status: {})",
                        email, user.getSubscriptionStatus());
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Active subscription required\",\"code\":\"SUBSCRIPTION_REQUIRED\"}");
                return;
            }

            filterChain.doFilter(request, response);
        } catch (Exception e) {
            log.error("Error checking subscription for user {}: {}", email, e.getMessage());
            // Allow request to proceed if there's an error (fail open for safety)
            filterChain.doFilter(request, response);
        }
    }

    private boolean hasActiveSubscription(User user) {
        // Creators, Admins, and Editors always have infinite subscription
        if (user.getRole() == com.cooked.backend.entity.Role.CREATOR || 
            user.getRole() == com.cooked.backend.entity.Role.ADMIN || 
            user.getRole() == com.cooked.backend.entity.Role.EDITOR) {
            return true;
        }

        SubscriptionStatus status = user.getSubscriptionStatus();
        LocalDateTime expiresAt = user.getSubscriptionExpiresAt();

        // Allow if status is ACTIVE or TRIAL
        if (status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.TRIAL) {
            // Check expiration date if set
            if (expiresAt != null) {
                return expiresAt.isAfter(LocalDateTime.now());
            }
            return true;
        }

        // Allow if INFINITE
        if (status == SubscriptionStatus.INFINITE) {
            return true;
        }

        // Deny if FREE, EXPIRED, or CANCELLED
        return false;
    }

    private boolean isPublicEndpoint(String path) {
        return path.equals("/") ||
                path.startsWith("/auth/") ||
                path.startsWith("/v3/api-docs") ||
                path.startsWith("/swagger-ui") ||
                path.startsWith("/subscriptions/plan") ||
                path.startsWith("/subscriptions/paywall-config") ||
                path.startsWith("/subscriptions/revenuecat-webhook") ||
                path.startsWith("/api/subscription/paywall-config") ||
                path.startsWith("/api/analytics/track") ||
                path.startsWith("/api/app-metrics/track") ||
                path.startsWith("/api/recipe-data") ||
                path.startsWith("/api/ingredients") ||
                path.startsWith("/recipes/popular") ||
                path.startsWith("/recipes/explore") ||
                path.startsWith("/recipes/top-creators") ||
                path.startsWith("/recipes/trending-ai") ||
                path.startsWith("/share") ||
                path.startsWith("/support/submit") ||
                path.startsWith("/.well-known") ||
                path.startsWith("/webhooks") ||
                path.startsWith("/actuator") ||
                path.startsWith("/ws");
    }
}
