package com.cooked.backend.dto.request;

import lombok.Data;

@Data
public class RecipeRequest {

    private String title;
    private String description;
    private String ingredients;
    private String instructions;
    private Long categoryId;
}