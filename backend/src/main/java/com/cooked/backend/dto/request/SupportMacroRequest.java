package com.cooked.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupportMacroRequest(@NotBlank @Size(max = 120) String title, @NotBlank @Size(max = 10000) String body) {
}
