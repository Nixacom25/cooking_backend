package com.cooked.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class RecipeResponse {
    private UUID id;
    private String name;
    private String image;
    private Integer cookTime;
    private Integer kcal;
    private List<RecipeIngredientResponse> ingredients;
    private List<String> steps;
    private boolean isPublic;
    private boolean isFavorite;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
