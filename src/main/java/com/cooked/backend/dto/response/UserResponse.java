package com.cooked.backend.dto.response;

import com.cooked.backend.entity.Role;
import com.cooked.backend.entity.Status;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class UserResponse {
    private UUID id;
    private String firstname;
    private String lastname;
    private String phone;
    private String email;
    private Role role;
    private Status status;
    private LocalDateTime createdAt;
}