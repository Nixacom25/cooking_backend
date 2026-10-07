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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        AdminUserFilter filter = new AdminUserFilter(q, platform, subscription, signupDays, source, status, trial, partner);
        PageRequest pageable = PageRequest.of(PaginationUtils.clampPage(page), PaginationUtils.clampSize(size), Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(users.search(filter, pageable));
    }

    @Operation(summary = "Discovery sources answered in onboarding (filter options)")
    @GetMapping("/sources")
    public ResponseEntity<List<String>> sources() {
        return ResponseEntity.ok(users.sources());
    }
}
