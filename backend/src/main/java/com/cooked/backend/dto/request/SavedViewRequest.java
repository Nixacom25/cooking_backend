package com.cooked.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** query = JSON object of filter values, as the backoffice screen stores it. */
public record SavedViewRequest(
        @NotBlank @Size(max = 60) @Pattern(regexp = "[a-z0-9-]+") String screen,
        @NotBlank @Size(max = 80) String name,
        @NotBlank @Size(max = 4000) String query) {
}
