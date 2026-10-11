package com.cooked.backend.controller;

import com.cooked.backend.dto.response.MyRoleResponse;
import com.cooked.backend.service.CustomRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in staff member's custom role, so the backoffice hides areas they can't open. */
@RestController
@RequiredArgsConstructor
public class StaffRoleController {

    private final CustomRoleService service;

    @GetMapping("/api/admin/roles/me")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<MyRoleResponse> me(Authentication auth) {
        return ResponseEntity.ok(service.me(auth.getName()));
    }
}
