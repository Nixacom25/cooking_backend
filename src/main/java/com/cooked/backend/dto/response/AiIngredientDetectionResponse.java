package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.cooked.backend.dto.request.IngredientPayload;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiIngredientDetectionResponse {
    private boolean success;
    private List<IngredientPayload> allowed_ingredients;
    private List<RestrictedIngredient> restricted_ingredients;
    private String image_url;
    private Double confidence;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RestrictedIngredient {
        private String name;
        private String icon;
        private String reason;
    }
}
