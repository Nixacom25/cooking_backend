package com.cooked.backend.security;

import com.cooked.backend.service.WorkspaceSettingsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * When Settings → "Require 2FA for all admins" is on, admin API calls need a
 * token carrying the "mfa" claim (obtained via /api/admin/2fa/verify).
 * Runs after Spring Security, so the caller is already authenticated.
 */
@Component
public class AdminTwoFactorFilter extends OncePerRequestFilter {

    static final String ADMIN_PREFIX = "/api/admin/";
    static final String TWO_FA_PREFIX = "/api/admin/2fa";
    private static final long CACHE_MS = 30_000;

    private final WorkspaceSettingsService settings;
    private final JwtService jwtService;
    private volatile boolean required;
    private volatile long checkedAt;

    public AdminTwoFactorFilter(WorkspaceSettingsService settings, JwtService jwtService) {
        this.settings = settings;
        this.jwtService = jwtService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null || !uri.startsWith(ADMIN_PREFIX) || uri.startsWith(TWO_FA_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean admin = auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        String header = request.getHeader("Authorization");
        if (admin && isRequired() && (header == null || !header.startsWith("Bearer ") || !jwtService.hasMfa(header.substring(7)))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":401,\"errorCode\":\"MFA_REQUIRED\",\"message\":\"Confirm the code sent by email to continue.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isRequired() {
        long now = System.currentTimeMillis();
        if (now - checkedAt > CACHE_MS) {
            required = settings.current().isRequire2fa();
            checkedAt = now;
        }
        return required;
    }
}
