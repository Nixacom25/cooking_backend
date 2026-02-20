package com.cooked.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import com.cooked.backend.dto.request.RecipeRequest;
import com.cooked.backend.dto.response.RecipeResponse;
import com.cooked.backend.service.RecipeService;

import java.util.List;

@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
@CrossOrigin("*")
public class RecipeController {

    private final RecipeService recipeService;

    @PostMapping("/{userId}")
    public RecipeResponse create(
            @RequestBody RecipeRequest request,
            @PathVariable Long userId) {
        return recipeService.create(request, userId);
    }

    @GetMapping
    public List<RecipeResponse> getAll() {
        return recipeService.getAll();
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        recipeService.delete(id);
    }
}
