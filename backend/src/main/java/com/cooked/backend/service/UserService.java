package com.cooked.backend.service;

import com.cooked.backend.dto.request.CreateUserRequest;
import com.cooked.backend.dto.request.UpdatePasswordRequest;
import com.cooked.backend.dto.request.UpdateUserRequest;
import com.cooked.backend.dto.request.UpdateUserStatusRequest;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UserService {
    UserResponse getCurrentUser(String email);

    UserResponse updateCurrentUser(String email, UpdateUserRequest request);

    MessageResponse updatePreferences(String email, com.cooked.backend.dto.request.UpdatePreferencesRequest request);

    MessageResponse updatePassword(String email, UpdatePasswordRequest request);

    MessageResponse uploadProfilePhoto(String email, org.springframework.web.multipart.MultipartFile file);

    Page<UserResponse> getClients(Pageable pageable);

    UserResponse createClient(CreateUserRequest request);

    UserResponse updateClient(UUID id, UpdateUserRequest request);

    MessageResponse deleteClient(UUID id);

    Page<UserResponse> getAdmins(Pageable pageable);

    UserResponse createAdmin(CreateUserRequest request);

    UserResponse updateAdmin(UUID id, UpdateUserRequest request);

    MessageResponse deleteAdmin(UUID id);

    MessageResponse updateUserStatus(UUID id, UpdateUserStatusRequest request);
}
