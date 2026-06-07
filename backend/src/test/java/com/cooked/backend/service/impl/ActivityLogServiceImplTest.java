package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.ActivityLogResponse;
import com.cooked.backend.entity.ActivityLog;
import com.cooked.backend.entity.User;
import com.cooked.backend.repository.ActivityLogRepository;
import com.cooked.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ActivityLogServiceImplTest {

    @Mock
    private ActivityLogRepository activityLogRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ActivityLogServiceImpl activityLogService;

    private User dummyUser;

    @BeforeEach
    void setUp() {
        dummyUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .build();
    }

    @Test
    void testLogActivity_Success() {
        when(activityLogRepository.save(any(ActivityLog.class))).thenAnswer(i -> i.getArgument(0));

        activityLogService.logActivity(dummyUser, "Test Title", "Test Message");

        verify(activityLogRepository).save(any(ActivityLog.class));
    }

    @Test
    void testGetMyActivities_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        ActivityLog log = ActivityLog.builder()
                .id(UUID.randomUUID())
                .title("Test")
                .message("Message")
                .user(dummyUser)
                .build();
                
        Page<ActivityLog> pagedResult = new PageImpl<>(List.of(log));

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(dummyUser));
        when(activityLogRepository.findAllByUserIdOrderByCreatedAtDesc(dummyUser.getId(), pageable)).thenReturn(pagedResult);

        Page<ActivityLogResponse> response = activityLogService.getMyActivities("test@example.com", pageable);

        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
        assertEquals("Test", response.getContent().get(0).getTitle());
    }
}
