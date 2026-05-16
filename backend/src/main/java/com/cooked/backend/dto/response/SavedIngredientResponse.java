package com.cooked.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class SavedIngredientResponse {
    private UUID id;
    private String name;
    private String icon;
    private LocalDateTime createdAt;
}
