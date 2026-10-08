package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.CreateUserRequest;
import com.cooked.backend.dto.request.TeamInviteRequest;
import com.cooked.backend.dto.response.UserResponse;
import com.cooked.backend.entity.Role;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.EmailService;
import com.cooked.backend.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminTeamServiceImplTest {

    @Mock private UserService userService;
    @Mock private UserRepository users;
    @Mock private EmailService email;
    @InjectMocks private AdminTeamServiceImpl service;

    @Test
    void inviteCreatesTheAccountWithAHiddenPasswordAndEmailsTheLink() {
        when(userService.createAdmin(any())).thenReturn(new UserResponse());
        service.invite(new TeamInviteRequest(" Awa@Cooked.app ", "Awa", "", "EDITOR", "https://admin.cooked.app/"));

        ArgumentCaptor<CreateUserRequest> req = ArgumentCaptor.forClass(CreateUserRequest.class);
        verify(userService).createAdmin(req.capture());
        assertEquals("awa@cooked.app", req.getValue().getEmail());
        assertEquals(Role.EDITOR, req.getValue().getRole());
        assertNull(req.getValue().getLastname());
        assertTrue(req.getValue().getPassword().length() >= 32);
        verify(email).sendTeamInviteEmail("awa@cooked.app", "Awa", "Data intern", "https://admin.cooked.app/forgot-password");
    }

    @Test
    void latestOfTwoDates() {
        LocalDateTime a = LocalDateTime.of(2026, 10, 1, 9, 0), b = a.plusDays(1);
        assertEquals(b, AdminTeamServiceImpl.latest(a, b));
        assertEquals(a, AdminTeamServiceImpl.latest(a, null));
        assertNull(AdminTeamServiceImpl.latest(null, null));
    }
}
