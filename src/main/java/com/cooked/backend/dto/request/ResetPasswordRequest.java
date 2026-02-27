package com.cooked.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank(message = "Identifier (email or phone) is required")
    private String identifier;

    @NotBlank
    private String newPassword;
}
