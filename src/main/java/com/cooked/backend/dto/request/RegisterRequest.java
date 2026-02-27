package com.cooked.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {

    @Parameter(description = "User's first name")
    @NotBlank
    private String firstname;

    @Parameter(description = "User's last name")
    @NotBlank
    private String lastname;

    @Parameter(description = "User's phone number")
    private String phone;

    @Parameter(description = "User's email address")
    @Email
    @NotBlank
    private String email;

    @Parameter(description = "User's password")
    private String password;

    @Parameter(description = "Profile photo URL")
    private String photo;

    @Parameter(description = "Provider (LOCAL, GOOGLE, APPLE)")
    private String provider; // e.g. "LOCAL", "GOOGLE", "APPLE"
}
