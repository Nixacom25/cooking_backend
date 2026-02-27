package com.cooked.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateCookbookRequest {
    @NotBlank(message = "Cookbook name is required")
    private String name;
}
