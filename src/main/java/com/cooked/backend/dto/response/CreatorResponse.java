package com.cooked.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class CreatorResponse {
    private UUID id;
    private String firstname;
    private String lastname;
    private String photo;
    private long publicRecipeCount;
    private long totalUsageCount;
}
