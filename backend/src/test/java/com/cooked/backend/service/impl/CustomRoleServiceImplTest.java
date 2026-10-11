package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.CustomRoleRequest;
import com.cooked.backend.entity.CustomRole;
import com.cooked.backend.entity.Role;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.repository.CustomRoleRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.security.CustomRoleFilter;
import com.cooked.backend.service.ActivityLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomRoleServiceImplTest {

    @Mock private CustomRoleRepository roles;
    @Mock private UserRepository users;
    @Mock private ActivityLogService activityLog;
    @Mock private CustomRoleFilter filter;
    @InjectMocks private CustomRoleServiceImpl service;

    private User full;
    private User restricted;
    private CustomRole role;

    @BeforeEach
    void setUp() {
        role = CustomRole.builder().id(UUID.randomUUID()).name("Support Lead").permissions("support=EDIT").build();
        full = User.builder().id(UUID.randomUUID()).email("owner@x.com").role(Role.ADMIN).build();
        restricted = User.builder().id(UUID.randomUUID()).email("lead@x.com").role(Role.ADMIN).build();
        restricted.setCustomRoleId(role.getId());
        lenient().when(users.findByEmail("owner@x.com")).thenReturn(Optional.of(full));
        lenient().when(users.findByEmail("lead@x.com")).thenReturn(Optional.of(restricted));
        lenient().when(roles.findById(role.getId())).thenReturn(Optional.of(role));
        lenient().when(roles.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void aRoleMemberCannotWidenTheirOwnAccess() {
        CustomRoleRequest all = new CustomRoleRequest("Support Lead", null, Map.of("settings", "EDIT", "revenue", "EDIT"));
        assertThrows(AccessDeniedException.class, () -> service.update(role.getId(), all, "lead@x.com"));
        assertThrows(AccessDeniedException.class, () -> service.create(all, "lead@x.com"));
        assertThrows(AccessDeniedException.class, () -> service.removeMember(role.getId(), restricted.getId(), "lead@x.com"));
    }

    @Test
    void cannotAssignYourselfOrTheLastFullAdmin() {
        when(users.findById(full.getId())).thenReturn(Optional.of(full));
        assertThrows(BadRequestException.class, () -> service.addMember(role.getId(), full.getId(), "owner@x.com"));

        User other = User.builder().id(UUID.randomUUID()).email("other@x.com").role(Role.ADMIN).build();
        when(users.findById(other.getId())).thenReturn(Optional.of(other));
        when(users.countByRoleAndCustomRoleIdIsNull(Role.ADMIN)).thenReturn(1L);
        assertThrows(BadRequestException.class, () -> service.addMember(role.getId(), other.getId(), "owner@x.com"));

        when(users.countByRoleAndCustomRoleIdIsNull(Role.ADMIN)).thenReturn(2L);
        service.addMember(role.getId(), other.getId(), "owner@x.com");
        assertEquals(role.getId(), other.getCustomRoleId());
    }

    @Test
    void onlyAdminsCanGetARoleAndRolesWithMembersStay() {
        User intern = User.builder().id(UUID.randomUUID()).email("i@x.com").role(Role.EDITOR).build();
        when(users.findById(intern.getId())).thenReturn(Optional.of(intern));
        assertThrows(BadRequestException.class, () -> service.addMember(role.getId(), intern.getId(), "owner@x.com"));
        when(users.countByCustomRoleId(role.getId())).thenReturn(1L);
        assertThrows(BadRequestException.class, () -> service.delete(role.getId(), "owner@x.com"));
    }

    @Test
    void unknownPermissionValueIsRefusedAndChangesApplyAtOnce() {
        assertThrows(BadRequestException.class, () -> service.update(role.getId(), new CustomRoleRequest("Support Lead", null, Map.of("users", "ADMIN")), "owner@x.com"));
        service.update(role.getId(), new CustomRoleRequest("Support Lead", "Tickets", Map.of("users", "VIEW")), "owner@x.com");
        assertTrue(role.getPermissions().contains("users=VIEW"));
        verify(filter).evict(role.getId());
    }
}
