package com.cooked.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Per-user columns of the admin Users table that are not on the user record. */
public record UserExtrasResponse(String platform, BigDecimal revenue, LocalDateTime lastSeen) {
}
