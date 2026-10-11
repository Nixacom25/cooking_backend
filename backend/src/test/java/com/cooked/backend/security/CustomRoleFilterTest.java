package com.cooked.backend.security;

import com.cooked.backend.entity.CustomRole;
import com.cooked.backend.entity.Role;
import com.cooked.backend.entity.User;
import com.cooked.backend.repository.CustomRoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomRoleFilterTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse call(User u, String method, String path, CustomRoleRepository roles) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, List.of()));
        MockHttpServletRequest req = new MockHttpServletRequest(method, path);
        MockHttpServletResponse res = new MockHttpServletResponse();
        new CustomRoleFilter(roles).doFilter(req, res, new MockFilterChain());
        return res;
    }

    @Test
    void roleMembersAreLimitedFullAdminsAreNot() throws Exception {
        UUID roleId = UUID.randomUUID();
        CustomRoleRepository roles = mock(CustomRoleRepository.class);
        when(roles.findById(roleId)).thenReturn(Optional.of(CustomRole.builder().id(roleId).name("Viewer").permissions("revenue=VIEW").build()));
        User member = User.builder().id(UUID.randomUUID()).email("m@x.com").role(Role.ADMIN).build();
        member.setCustomRoleId(roleId);
        User full = User.builder().id(UUID.randomUUID()).email("f@x.com").role(Role.ADMIN).build();

        assertEquals(200, call(member, "GET", "/api/admin/revenue/summary", roles).getStatus());
        assertEquals(403, call(member, "PATCH", "/api/admin/revenue/payments/1/status", roles).getStatus());
        assertEquals(403, call(member, "GET", "/api/admin/costs/overview", roles).getStatus());
        assertEquals(200, call(full, "DELETE", "/api/admin/roles/1", roles).getStatus());
    }
}
