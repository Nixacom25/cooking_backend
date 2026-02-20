package com.cooked.backend.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.cooked.backend.dto.request.RecipeRequest;
import com.cooked.backend.dto.response.RecipeResponse;
import com.cooked.backend.entity.Category;
import com.cooked.backend.entity.Recipe;
import com.cooked.backend.entity.User;
import com.cooked.backend.repository.CategoryRepository;
import com.cooked.backend.repository.RecipeRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.RecipeService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecipeServiceImpl implements RecipeService {

        private final RecipeRepository recipeRepository;
        private final CategoryRepository categoryRepository;
        private final UserRepository userRepository;

        @Override
        public RecipeResponse create(RecipeRequest request, Long userId) {

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

                Category category = categoryRepository.findById(request.getCategoryId())
                                .orElseThrow(() -> new RuntimeException("Catégorie introuvable"));

                Recipe recipe = Recipe.builder()
                                .title(request.getTitle())
                                .description(request.getDescription())
                                .ingredients(request.getIngredients())
                                .instructions(request.getInstructions())
                                .createdAt(LocalDateTime.now())
                                .user(user)
                                .category(category)
                                .build();

                recipeRepository.save(recipe);

                return RecipeResponse.builder()
                                .id(recipe.getId())
                                .title(recipe.getTitle())
                                .description(recipe.getDescription())
                                .ingredients(recipe.getIngredients())
                                .instructions(recipe.getInstructions())
                                .categoryName(category.getName())
                                .authorName(user.getFullName())
                                .build();
        }

        @Override
        public List<RecipeResponse> getAll() {

                return recipeRepository.findAll()
                                .stream()
                                .map(recipe -> RecipeResponse.builder()
                                                .id(recipe.getId())
                                                .title(recipe.getTitle())
                                                .description(recipe.getDescription())
                                                .ingredients(recipe.getIngredients())
                                                .instructions(recipe.getInstructions())
                                                .categoryName(recipe.getCategory().getName())
                                                .authorName(recipe.getUser().getFullName())
                                                .build())
                                .collect(Collectors.toList());
        }

        @Override
        public void delete(Long id) {
                recipeRepository.deleteById(id);
        }
}
