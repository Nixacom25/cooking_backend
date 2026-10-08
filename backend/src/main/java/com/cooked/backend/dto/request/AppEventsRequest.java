package com.cooked.backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Batch of events the app sends (at most 50 per call). */
public record AppEventsRequest(@NotEmpty @Size(max = 50) List<@Valid Event> events) {

    public record Event(@NotNull String type, @Size(max = 160) String detail, @Min(0) @Max(86_400_000) Integer durationMs) {
    }
}
