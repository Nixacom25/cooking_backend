package com.cooked.backend.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Move [count] unfinished recipes (assigned / in progress) from one intern to another. */
public record BatchReassignRequest(@NotNull UUID fromUserId, @NotNull UUID toUserId, @Min(1) @Max(1000) int count) {
}
