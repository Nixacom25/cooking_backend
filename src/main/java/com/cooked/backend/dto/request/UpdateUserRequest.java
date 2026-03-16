package com.cooked.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateUserRequest {
    @NotBlank
    private String firstname;

    @NotBlank
    private String lastname;

    private String phone;

    private String discoverySource;
    private String otherDiscoverySource;
}
