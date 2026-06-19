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
                .map(this::mapToResponse);
    }

    @Override
    public Page<ActivityLogResponse> getActivitiesByRole(com.cooked.backend.entity.Role role, Pageable pageable) {
        return activityLogRepository.findAllByUserRoleOrderByCreatedAtDesc(role, pageable)
                .map(this::mapToResponse);
    }

    @Override
    public void logDetailedEditorActivity(User editor, java.util.List<String> changedFields, String entityType, String entityName, String parentEntityName) {
        if (changedFields == null || changedFields.isEmpty()) {
            return;
        }

        String fieldsString = String.join(" et ", changedFields);
        
        String message;
        if (parentEntityName != null && !parentEntityName.isEmpty()) {
            message = String.format("%s vient de modifier %s du %s %s de la cuisine %s", 
                editor.getFirstname(), fieldsString, entityType, entityName, parentEntityName);
        } else {
            message = String.format("%s vient de modifier %s du %s %s", 
                editor.getFirstname(), fieldsString, entityType, entityName);
        }

        logActivity(editor, "Modification Editor", message);
    }

    private ActivityLogResponse mapToResponse(ActivityLog log) {
        return ActivityLogResponse.builder()
                .id(log.getId())
                .title(log.getTitle())
                .message(log.getMessage())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
