package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.CreateUserRequest;
import com.cooked.backend.dto.request.UpdatePasswordRequest;
import com.cooked.backend.dto.request.UpdateUserRequest;
import com.cooked.backend.dto.request.UpdateUserStatusRequest;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.dto.response.UserResponse;
import com.cooked.backend.entity.Role;
import com.cooked.backend.entity.Status;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.EmailAlreadyExistsException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.mapper.UserMapper;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return userMapper.toResponse(user);
    }

    @Override
    public UserResponse updateCurrentUser(String email, UpdateUserRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.getPhone().equals(request.getPhone()) && userRepository.existsByPhone(request.getPhone())) {
            throw new BadRequestException("Phone number already exists");
        }

        user.setFirstname(request.getFirstname());
        user.setLastname(request.getLastname());
        user.setPhone(request.getPhone());
        userRepository.save(user);

        return userMapper.toResponse(user);
    }

    @Override
    public MessageResponse updatePassword(String email, UpdatePasswordRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BadRequestException("Incorrect old password");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        return new MessageResponse("Password updated successfully");
    }

    @Override
    public Page<UserResponse> getClients(Pageable pageable) {
        return userRepository.findAllByRole(Role.CLIENT, pageable)
                .map(userMapper::toResponse);
    }

    @Override
    public UserResponse createClient(CreateUserRequest request) {
        return createUserWithRole(request, Role.CLIENT);
    }

    @Override
    public UserResponse updateClient(UUID id, UpdateUserRequest request) {
        return updateUser(id, request, Role.CLIENT);
    }

    @Override
    public MessageResponse deleteClient(UUID id) {
        userRepository.deleteById(id);
        return new MessageResponse("Client deleted successfully");
    }

    @Override
    public Page<UserResponse> getAdmins(Pageable pageable) {
        return userRepository.findAllByRole(Role.ADMIN, pageable)
                .map(userMapper::toResponse);
    }

    @Override
    public UserResponse createAdmin(CreateUserRequest request) {
        return createUserWithRole(request, Role.ADMIN);
    }

    @Override
    public UserResponse updateAdmin(UUID id, UpdateUserRequest request) {
        return updateUser(id, request, Role.ADMIN);
    }

    @Override
    public MessageResponse deleteAdmin(UUID id) {
        userRepository.deleteById(id);
        return new MessageResponse("Admin deleted successfully");
    }

    @Override
    public MessageResponse updateUserStatus(UUID id, UpdateUserStatusRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setStatus(request.getStatus());
        userRepository.save(user);

        return new MessageResponse("User status updated successfully");
    }

    private UserResponse createUserWithRole(CreateUserRequest request, Role role) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException("Email already exists");
        }
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new BadRequestException("Phone number already exists");
        }

        User user = User.builder()
                .firstname(request.getFirstname())
                .lastname(request.getLastname())
                .phone(request.getPhone())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .status(Status.ACTIVE)
                .build();

        userRepository.save(user);
        return userMapper.toResponse(user);
    }

    private UserResponse updateUser(UUID id, UpdateUserRequest request, Role expectedRole) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getRole() != expectedRole) {
            throw new BadRequestException("User is not a " + expectedRole.name());
        }

        if (!user.getPhone().equals(request.getPhone()) && userRepository.existsByPhone(request.getPhone())) {
            throw new BadRequestException("Phone number already exists");
        }

        user.setFirstname(request.getFirstname());
        user.setLastname(request.getLastname());
        user.setPhone(request.getPhone());
        userRepository.save(user);

        return userMapper.toResponse(user);
    }

    @Override
    public MessageResponse uploadProfilePhoto(String email, MultipartFile file) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please select a file to upload");
        }

        try {
            String uploadDir = "uploads/profiles/";
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            String filename = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            Path filePath = uploadPath.resolve(filename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            user.setPhoto("/" + uploadDir + filename);
            userRepository.save(user);

            return new MessageResponse("Profile photo updated successfully");
        } catch (IOException e) {
            throw new RuntimeException("Could not store file", e);
        }
    }
}
