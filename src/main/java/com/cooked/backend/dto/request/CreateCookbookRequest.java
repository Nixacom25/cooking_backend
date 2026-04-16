package com.cooked.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CreateCookbookRequest {
    @NotBlank(message = "Cookbook name is required")
    private String name;

    private List<UUID> recipeIds;
}
