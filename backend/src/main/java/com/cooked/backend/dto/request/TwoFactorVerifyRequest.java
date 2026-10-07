package com.cooked.backend.dto.request;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class TwoFactorVerifyRequest {
    @Pattern(regexp = "\\d{6}", message = "Enter the 6-digit code")
    private String code;
}
