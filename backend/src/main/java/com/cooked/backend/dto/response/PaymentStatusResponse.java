package com.cooked.backend.dto.response;

import java.util.UUID;

public record PaymentStatusResponse(UUID id, String previousStatus, String status) {
}
