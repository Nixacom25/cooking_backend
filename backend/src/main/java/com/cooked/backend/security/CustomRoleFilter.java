package com.cooked.backend.security;

import com.cooked.backend.entity.CustomRole;
import com.cooked.backend.entity.Role;
import com.cooked.backend.entity.User;
import com.cooked.backend.repository.CustomRoleRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies custom roles: an ADMIN with a custom role only reaches the areas
 * the role allows (View = read, Edit = read and change). Full admins and
 * everyone else are not affected. Permissions are cached 30 s per role.
 */
@Component
public class CustomRoleFilter extends OncePerRequestFilter {

    private static final long TTL_MS = 30_000;

    private final CustomRoleRepository roles;
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(Map<String, RolePolicy.Level> perms, long at) {}

    public CustomRoleFilter(CustomRoleRepository roles) {
        this.roles = roles;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User u && u.getRole() == Role.ADMIN && u.getCustomRoleId() != null) {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            Map<String, RolePolicy.Level> perms = permissions(u.getCustomRoleId());
            if (!RolePolicy.allowed(perms, request.getMethod(), path)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Your role doesn't allow this.\",\"errorCode\":\"ROLE_FORBIDDEN\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private Map<String, RolePolicy.Level> permissions(UUID roleId) {
        Cached c = cache.get(roleId);
        if (c != null && System.currentTimeMillis() - c.at() < TTL_MS) return c.perms();
        // A deleted role leaves its former members with nothing but the common paths.
        Map<String, RolePolicy.Level> perms = roles.findById(roleId).map(CustomRole::getPermissions).map(RolePolicy::parse)
                .orElseGet(() -> RolePolicy.parse(null));
        cache.put(roleId, new Cached(perms, System.currentTimeMillis()));
        return perms;
    }

    /** Called when a role changes so the new permissions apply at once. */
    public void evict(UUID roleId) {
        cache.remove(roleId);
    }
}
