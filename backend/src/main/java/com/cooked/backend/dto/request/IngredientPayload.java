package com.cooked.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngredientPayload {
    @NotBlank(message = "Ingredient name is required")
    private String name;

    private String icon;
    private String quantity;
    private Double price;

    public IngredientPayload(String name, String quantity, String icon) {
        this.name = name;
        this.quantity = quantity;
        this.icon = icon;
    }
}
