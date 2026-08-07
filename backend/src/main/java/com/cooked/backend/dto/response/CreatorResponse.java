package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorResponse {
    private UUID id;
    private String firstname;
    private String lastname;
    private String email;
    private String photo;
    private long publicRecipeCount;
    private long totalUsageCount;
}
