package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiIngredientDetectionResponse {
    private List<String> allowed_ingredients;
    private List<String> restricted_ingredients;
    private Double confidence;
}
