package com.cooked.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Correction of a recorded payment: only SUCCESS counts as revenue. */
public record PaymentStatusRequest(
        @NotBlank @Pattern(regexp = "SUCCESS|SANDBOX|DUPLICATE|REFUNDED|FAILED") String status) {
}
