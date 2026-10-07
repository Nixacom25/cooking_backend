package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** A recipe creator (user with public recipes) for the Creator detail screen. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorDetailResponse {
    private UUID id;
    private String name;
    private String email;
    private String photo;
    private String role;
    private LocalDateTime joined;
    private long publicRecipes;
    private long published30d;
    private long publishedPrev30d;
    /** Times their recipes were added to grocery lists. */
    private long uses;
    private List<AcquisitionResponse.DayCount> publishedDaily;
    private List<Recipe> topRecipes;
    /** Ambassador record with the same email, if any. */
    private UUID ambassadorId;
    private String ambassadorCode;

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class Recipe {
        private UUID id;
        private String name;
        private String image;
        private long uses;
        private LocalDateTime createdAt;
    }
}
