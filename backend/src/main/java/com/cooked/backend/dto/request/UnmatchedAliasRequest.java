package com.cooked.backend.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Adds the unmatched name as an alias of this visual. */
public record UnmatchedAliasRequest(@NotNull UUID visualId) {
}
