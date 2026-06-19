package com.cooked.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateUserRequest {
    private String firstname;

    private String lastname;

    private String phone;

    @Email
    @NotBlank
    private String email;

    @NotBlank
    private String password;

    private com.cooked.backend.entity.Role role;
}
