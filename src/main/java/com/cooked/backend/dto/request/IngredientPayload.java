package com.cooked.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class IngredientPayload {
    @NotBlank(message = "Ingredient name is required")
    private String name;

    // Optional icon or emoji
    private String icon;

    @NotBlank(message = "Quantity is required (e.g. 250g)")
    private String quantity;
}
