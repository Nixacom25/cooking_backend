package com.cooked.backend.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Alert keys to mark as read (at most 200 at once). */
public record AlertAckRequest(@NotEmpty @Size(max = 200) List<@Size(min = 1, max = 200) String> keys) {
}
