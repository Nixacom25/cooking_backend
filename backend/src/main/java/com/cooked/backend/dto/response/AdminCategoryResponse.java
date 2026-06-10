package com.cooked.backend.dto.response;

import com.cooked.backend.entity.CategoryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCategoryResponse {
    private UUID id;
    private String name;
    private String image;
    private CategoryType type;
    private boolean active;
    private long recipeCount;
}
