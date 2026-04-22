package com.cooked.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoginRequest {

    @NotBlank(message = "Identifier (email or phone) is required")
    private String identifier;

    private String password;

    private String provider; // e.g. "LOCAL", "GOOGLE", "APPLE"

    private boolean isSignup;
    private String firstname;
    private String lastname;
    private String phone;
}