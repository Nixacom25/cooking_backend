package com.cooked.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/** permissions: area → NONE | VIEW | EDIT (areas: users, recipes, revenue, costs, growth, support, analytics, settings). */
public record CustomRoleRequest(@NotBlank @Size(max = 60) String name, @Size(max = 300) String description,
                                @NotNull Map<String, String> permissions) {
}
