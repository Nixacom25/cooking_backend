package com.cooked.backend.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RecipeResponse {

    private Long id;
    private String title;
    private String description;
    private String ingredients;
    private String instructions;
    private String categoryName;
    private String authorName;
}
