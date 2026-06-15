package com.cooked.backend.controller;

import com.cooked.backend.dto.response.IngredientResponse;
import com.cooked.backend.repository.IngredientRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ingredients")
@CrossOrigin(origins = "*")
public class IngredientController {

    private final IngredientRepository ingredientRepository;

    public IngredientController(IngredientRepository ingredientRepository) {
        this.ingredientRepository = ingredientRepository;
    }

    @GetMapping
    public ResponseEntity<List<IngredientResponse>> getAllIngredients() {
        List<IngredientResponse> responses = ingredientRepository.findAll().stream()
                .map(i -> IngredientResponse.builder()
                        .id(i.getId())
                        .name(i.getName())
                        .icon(i.getIcon())
                        .image(i.getImage())
                        .price(i.getPrice())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }
}
