package com.cooked.backend.controller;

import com.cooked.backend.dto.response.ActivityLogResponse;
import com.cooked.backend.service.ActivityLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/activities")
@RequiredArgsConstructor
@Tag(name = "Activity Log", description = "Endpoints for viewing user activity history")
@SecurityRequirement(name = "bearerAuth")
public class ActivityController {

    private final ActivityLogService activityLogService;

    @Operation(summary = "Get my activity logs (paginated)")
    @GetMapping
    public ResponseEntity<Page<ActivityLogResponse>> getMyActivities(Authentication auth, Pageable pageable) {
        return ResponseEntity.ok(activityLogService.getMyActivities(auth.getName(), pageable));
    }

    @Operation(summary = "Get editor activity logs (paginated) for Admin")
    @GetMapping("/editors")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<ActivityLogResponse>> getEditorActivities(Pageable pageable) {
        return ResponseEntity.ok(activityLogService.getActivitiesByRole(com.cooked.backend.entity.Role.EDITOR, pageable));
    }

    @Operation(summary = "Audit log: intern activity with filters (person = email, area = entity type, action = title, days, q)")
    @GetMapping("/editors/search")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<ActivityLogResponse>> searchEditorActivities(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String person,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String area,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String action,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Integer days,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String q,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size) {
        var pageable = org.springframework.data.domain.PageRequest.of(com.cooked.backend.util.PaginationUtils.clampPage(page), com.cooked.backend.util.PaginationUtils.clampSize(size));
        return ResponseEntity.ok(activityLogService.searchEditorActivities(new com.cooked.backend.dto.request.AdminAuditFilter(person, area, action, days, q), pageable));
    }

    @Operation(summary = "Audit log filter options (people, areas, actions)")
    @GetMapping("/editors/options")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<com.cooked.backend.dto.response.AuditOptionsResponse> editorActivityOptions() {
        return ResponseEntity.ok(activityLogService.editorActivityOptions());
    }
}
