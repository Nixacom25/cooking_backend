package com.cooked.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class RecipeIngredientResponse {
    private UUID id; // Optional (The real Ingredient UUID)
    private String name;
    private String icon;
    private String quantity;
}
