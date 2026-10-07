package com.cooked.backend.service;

import com.cooked.backend.dto.response.ActivityLogResponse;
import com.cooked.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ActivityLogService {
    void logActivity(User user, String title, String message);
    void logActivity(User user, String title, String message, java.util.UUID entityId, String entityType);

    Page<ActivityLogResponse> getMyActivities(String userEmail, Pageable pageable);

    Page<ActivityLogResponse> getActivitiesByRole(com.cooked.backend.entity.Role role, Pageable pageable);

    /** Intern activity with the Audit log filters, newest first. */
    Page<ActivityLogResponse> searchEditorActivities(com.cooked.backend.dto.request.AdminAuditFilter filter, Pageable pageable);

    /** People, areas and actions present in the intern activity (filter options). */
    com.cooked.backend.dto.response.AuditOptionsResponse editorActivityOptions();

    void logDetailedEditorActivity(User editor, java.util.List<String> changedFields, String entityType, String entityName, String parentEntityName, java.util.UUID entityId);
}
