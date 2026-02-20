package com.cooked.backend.service;

import java.util.List;

import com.cooked.backend.dto.request.RecipeRequest;
import com.cooked.backend.dto.response.RecipeResponse;

public interface RecipeService {

    RecipeResponse create(RecipeRequest request, Long userId);

    List<RecipeResponse> getAll();

    void delete(Long id);
}
