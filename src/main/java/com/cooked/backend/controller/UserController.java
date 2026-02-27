package com.cooked.backend.controller;

import com.cooked.backend.dto.request.CreateUserRequest;
import com.cooked.backend.dto.request.UpdatePasswordRequest;
import com.cooked.backend.dto.request.UpdateUserRequest;
import com.cooked.backend.dto.request.UpdateUserStatusRequest;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.dto.response.UserResponse;
import com.cooked.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.MediaType;

import java.util.UUID;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // --- Current User Routes ---

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(Authentication authentication) {
        return ResponseEntity.ok(userService.getCurrentUser(authentication.getName()));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateCurrentUser(Authentication authentication,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateCurrentUser(authentication.getName(), request));
    }

    @PutMapping("/password-reset")
    public ResponseEntity<MessageResponse> updatePassword(Authentication authentication,
            @Valid @RequestBody UpdatePasswordRequest request) {
        return ResponseEntity.ok(userService.updatePassword(authentication.getName(), request));
    }

    @Operation(summary = "Upload Profile Photo", description = "Uploads a profile picture for the authenticated user")
    @PostMapping(value = "/profile-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MessageResponse> uploadProfilePhoto(Authentication authentication,
            @Parameter(description = "Profile photo to upload") @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(userService.uploadProfilePhoto(authentication.getName(), file));
    }

    // --- Client Routes (ADMIN) ---

    @Operation(summary = "Get clients")
    @GetMapping("/client")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserResponse>> getClients(
            @Parameter(description = "Page number", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "10") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort format: property,asc|desc", example = "createdAt,desc") @RequestParam(defaultValue = "createdAt,desc") String sort) {

        String[] sortParams = sort.split(",");
        org.springframework.data.domain.Sort.Direction direction = sortParams.length > 1
                && sortParams[1].equalsIgnoreCase("asc") ? org.springframework.data.domain.Sort.Direction.ASC
                        : org.springframework.data.domain.Sort.Direction.DESC;
        Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by(direction, sortParams[0]));

        return ResponseEntity.ok(userService.getClients(pageable));
    }

    @PostMapping("/client")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> createClient(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.ok(userService.createClient(request));
    }

    @PutMapping("/client/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> updateClient(@PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateClient(id, request));
    }

    @DeleteMapping("/client/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> deleteClient(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.deleteClient(id));
    }

    // --- Admin Routes (ADMIN) ---

    @Operation(summary = "Get admins")
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserResponse>> getAdmins(
            @Parameter(description = "Page number", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "10") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort format: property,asc|desc", example = "createdAt,desc") @RequestParam(defaultValue = "createdAt,desc") String sort) {

        String[] sortParams = sort.split(",");
        org.springframework.data.domain.Sort.Direction direction = sortParams.length > 1
                && sortParams[1].equalsIgnoreCase("asc") ? org.springframework.data.domain.Sort.Direction.ASC
                        : org.springframework.data.domain.Sort.Direction.DESC;
        Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by(direction, sortParams[0]));

        return ResponseEntity.ok(userService.getAdmins(pageable));
    }

    @PostMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> createAdmin(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.ok(userService.createAdmin(request));
    }

    @PutMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> updateAdmin(@PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateAdmin(id, request));
    }

    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> deleteAdmin(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.deleteAdmin(id));
    }

    // --- Generic Status Update ---

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> updateUserStatus(@PathVariable UUID id,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        return ResponseEntity.ok(userService.updateUserStatus(id, request));
    }
}
