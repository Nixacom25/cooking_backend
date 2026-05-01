package com.cooked.backend.dto.response;

import com.cooked.backend.dto.request.CreateRecipeRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRecipeListResponse {
    private boolean success;
    private List<CreateRecipeRequest> recipes;
}
