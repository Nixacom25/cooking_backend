package com.cooked.backend.dto.response;

import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.cooked.backend.dto.request.IngredientPayload;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanResponse {
    private boolean success;
    private String user_id;
    private List<IngredientPayload> allowed_ingredients;
    private List<AiIngredientDetectionResponse.RestrictedIngredient> restricted_ingredients;
    private String image_url;
    private List<CreateRecipeRequest> recipes;
}
