package com.cooked.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstacartShoppingListRequest {
    private String title;
    private String landingPageTitle;
    private List<InstacartIngredientDto> ingredients;
}
