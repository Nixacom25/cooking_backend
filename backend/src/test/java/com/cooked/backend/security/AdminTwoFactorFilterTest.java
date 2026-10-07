package com.cooked.backend.security;

import com.cooked.backend.entity.WorkspaceSettings;
import com.cooked.backend.service.WorkspaceSettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminTwoFactorFilterTest {

    private final WorkspaceSettingsService settings = mock(WorkspaceSettingsService.class);
    private final JwtService jwt = mock(JwtService.class);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse call(String uri, String token) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("a", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res = new MockHttpServletResponse();
        new AdminTwoFactorFilter(settings, jwt).doFilter(req, res, new MockFilterChain());
        return res;
    }

    @Test
    void blocksAdminCallsWithoutMfaOnlyWhenRequired() throws Exception {
        WorkspaceSettings s = WorkspaceSettings.defaults();
        when(settings.current()).thenReturn(s);
        assertEquals(200, call("/api/admin/costs/overview", "plain").getStatus());       // not required

        s.setRequire2fa(true);
        AdminTwoFactorFilter f = new AdminTwoFactorFilter(settings, jwt);
        when(jwt.hasMfa("plain")).thenReturn(false);
        when(jwt.hasMfa("strong")).thenReturn(true);
        assertEquals(401, call("/api/admin/costs/overview", "plain").getStatus());
        assertTrue(call("/api/admin/costs/overview", "plain").getContentAsString().contains("MFA_REQUIRED"));
        assertEquals(200, call("/api/admin/costs/overview", "strong").getStatus());
        assertEquals(200, call("/api/admin/2fa/status", "plain").getStatus());          // the 2FA flow itself
        assertEquals(200, call("/recipes", "plain").getStatus());                       // app API untouched
    }
}
