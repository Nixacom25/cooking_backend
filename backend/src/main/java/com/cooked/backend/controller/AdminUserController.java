package com.cooked.backend.controller;

import com.cooked.backend.dto.request.AdminUserFilter;
import com.cooked.backend.dto.response.UserResponse;
import com.cooked.backend.service.AdminUserService;
import com.cooked.backend.util.PaginationUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin Users table (filters run in the database). HTTP only. */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Users", description = "Filtered list of app users")
public class AdminUserController {

    private final AdminUserService users;

    @Operation(summary = "Users, newest first, with optional filters (platform IOS|ANDROID|WEB, subscription, signupDays, source, status, trial, partner)")
    @GetMapping
    public ResponseEntity<Page<UserResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String subscription,
            @RequestParam(required = false) Integer signupDays,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean trial,
            @RequestParam(required = false) Boolean partner,
            @RequestParam(required = false) String version,
            @RequestParam(required = false) String country,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        AdminUserFilter filter = new AdminUserFilter(q, platform, subscription, signupDays, source, status, trial, partner, version, country);
        PageRequest pageable = PageRequest.of(PaginationUtils.clampPage(page), PaginationUtils.clampSize(size), Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(users.search(filter, pageable));
    }

    @Operation(summary = "Platform, lifetime revenue and last seen for up to 100 user ids")
    @GetMapping("/extras")
    public ResponseEntity<java.util.Map<java.util.UUID, com.cooked.backend.dto.response.UserExtrasResponse>> extras(@RequestParam List<java.util.UUID> ids) {
        if (ids.size() > 100) throw new com.cooked.backend.exception.BadRequestException("At most 100 ids");
        return ResponseEntity.ok(users.extras(ids));
    }

    @Operation(summary = "Users KPIs: clients, active in 30 days, on trial, churned in 30 days")
    @GetMapping("/summary")
    public ResponseEntity<com.cooked.backend.dto.response.UserSummaryResponse> summary() {
        return ResponseEntity.ok(users.summary());
    }

    @Operation(summary = "Block or unblock an app user (status ACTIVE | BLOCKED); blocked users lose access at once")
    @PutMapping("/{id}/status")
    public ResponseEntity<UserResponse> setStatus(@PathVariable java.util.UUID id, @RequestBody java.util.Map<String, String> body) {
        return ResponseEntity.ok(users.setStatus(id, body.get("status")));
    }

    @Operation(summary = "App versions and countries reported by the app (filter options)")
    @GetMapping("/client-context")
    public ResponseEntity<java.util.Map<String, List<String>>> clientContext() {
        return ResponseEntity.ok(users.clientContextOptions());
    }

    @Operation(summary = "Discovery sources answered in onboarding (filter options)")
    @GetMapping("/sources")
    public ResponseEntity<List<String>> sources() {
        return ResponseEntity.ok(users.sources());
    }
}
