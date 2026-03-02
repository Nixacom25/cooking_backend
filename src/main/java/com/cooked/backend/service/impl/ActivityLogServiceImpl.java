package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.ActivityLogResponse;
import com.cooked.backend.entity.ActivityLog;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.ActivityLogRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ActivityLogServiceImpl implements ActivityLogService {

    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;

    @Override
    @Async
    public void logActivity(User user, String title, String message) {
        ActivityLog log = ActivityLog.builder()
                .user(user)
                .title(title)
                .message(message)
                .build();
        activityLogRepository.save(log);
    }

    @Override
    public Page<ActivityLogResponse> getMyActivities(String userEmail, Pageable pageable) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return activityLogRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(log -> ActivityLogResponse.builder()
                        .id(log.getId())
                        .title(log.getTitle())
                        .message(log.getMessage())
                        .createdAt(log.getCreatedAt())
                        .build());
    }
}
