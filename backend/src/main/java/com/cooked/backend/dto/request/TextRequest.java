package com.cooked.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A single text body (support reply, internal note…). */
public record TextRequest(@NotBlank @Size(max = 10000) String body) {
}
