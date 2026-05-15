package com.cooked.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CookbookResponse {
    private UUID id;
    private String name;
    private List<RecipeResponse> recipes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean isPinned;
}
