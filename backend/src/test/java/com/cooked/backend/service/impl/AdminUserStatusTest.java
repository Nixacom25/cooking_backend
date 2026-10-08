package com.cooked.backend.service.impl;

import com.cooked.backend.entity.Role;
import com.cooked.backend.entity.Status;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.mapper.UserMapper;
import com.cooked.backend.repository.UserActivityDayRepository;
import com.cooked.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminUserStatusTest {

    private final UserRepository users = mock(UserRepository.class);
    private final AdminUserServiceImpl service = new AdminUserServiceImpl(users, mock(UserMapper.class), mock(UserActivityDayRepository.class));

    @Test
    void blocksAppUsersOnly() {
        UUID id = UUID.randomUUID();
        User client = User.builder().id(id).role(Role.CLIENT).status(Status.ACTIVE).build();
        when(users.findById(id)).thenReturn(Optional.of(client));
        when(users.save(any())).thenAnswer(i -> i.getArgument(0));
        service.setStatus(id, "blocked");
        assertEquals(Status.BLOCKED, client.getStatus());
        assertFalse(client.isAccountNonLocked());

        assertThrows(BadRequestException.class, () -> service.setStatus(id, "ARCHIVED"));
        UUID adminId = UUID.randomUUID();
        when(users.findById(adminId)).thenReturn(Optional.of(User.builder().id(adminId).role(Role.ADMIN).status(Status.ACTIVE).build()));
        assertThrows(BadRequestException.class, () -> service.setStatus(adminId, "BLOCKED"));
    }
}
