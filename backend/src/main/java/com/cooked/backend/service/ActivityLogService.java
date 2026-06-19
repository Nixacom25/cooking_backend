package com.cooked.backend.service;

import com.cooked.backend.dto.response.ActivityLogResponse;
import com.cooked.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ActivityLogService {
    void logActivity(User user, String title, String message);

    Page<ActivityLogResponse> getMyActivities(String userEmail, Pageable pageable);

    Page<ActivityLogResponse> getActivitiesByRole(com.cooked.backend.entity.Role role, Pageable pageable);

    void logDetailedEditorActivity(User editor, java.util.List<String> changedFields, String entityType, String entityName, String parentEntityName);
}
