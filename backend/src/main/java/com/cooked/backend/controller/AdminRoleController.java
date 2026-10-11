package com.cooked.backend.controller;

import com.cooked.backend.dto.request.CustomRoleRequest;
import com.cooked.backend.dto.response.CustomRoleResponse;
import com.cooked.backend.dto.response.MyRoleResponse;
import com.cooked.backend.service.CustomRoleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Settings → Permissions → custom roles. HTTP only: the work is in {@link CustomRoleService}. */
@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin custom roles", description = "Roles between Admin and Data Intern, per area None / View / Edit")
public class AdminRoleController {

    private final CustomRoleService service;

    @GetMapping
    public ResponseEntity<List<CustomRoleResponse>> list() {
        return ResponseEntity.ok(service.list());
    }

    @PostMapping
    public ResponseEntity<CustomRoleResponse> create(@Valid @RequestBody CustomRoleRequest body, Authentication auth) {
        return ResponseEntity.ok(service.create(body, auth.getName()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CustomRoleResponse> update(@PathVariable UUID id, @Valid @RequestBody CustomRoleRequest body, Authentication auth) {
        return ResponseEntity.ok(service.update(id, body, auth.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication auth) {
        service.delete(id, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/members/{userId}")
    public ResponseEntity<CustomRoleResponse> addMember(@PathVariable UUID id, @PathVariable UUID userId, Authentication auth) {
        return ResponseEntity.ok(service.addMember(id, userId, auth.getName()));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<CustomRoleResponse> removeMember(@PathVariable UUID id, @PathVariable UUID userId, Authentication auth) {
        return ResponseEntity.ok(service.removeMember(id, userId, auth.getName()));
    }
}
