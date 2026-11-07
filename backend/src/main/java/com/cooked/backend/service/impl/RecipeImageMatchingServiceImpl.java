package com.cooked.backend.service.impl;

import com.cooked.backend.entity.*;
import com.cooked.backend.repository.ImageLibraryRepository;
import com.cooked.backend.repository.RecipeImageMatchRepository;
import com.cooked.backend.repository.RecipeRepository;
import com.cooked.backend.service.*;
import com.cooked.backend.service.dto.RecipeTags;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecipeImageMatchingServiceImpl implements RecipeImageMatchingService {

    private static final Logger log = LoggerFactory.getLogger(RecipeImageMatchingServiceImpl.class);
    private final RecipeRepository recipeRepository;
    private final ImageLibraryRepository imageLibraryRepository;
    private final RecipeImageMatchRepository recipeImageMatchRepository;
    private final RecipeTagExtractor tagExtractor;

    @Value("${image.matching.library.threshold:0.75}")
    private double libraryThreshold;

    private static final Set<String> TITLE_STOP_WORDS = Set.of(
            "a", "an", "the", "and", "with", "of", "for", "recipe", "easy", "quick",
            "homemade", "classic", "simple", "delicious", "style");

    @Override
    @Async
    @Transactional
    public void matchAndAssignImageAsync(UUID recipeId) {
        Recipe recipe = recipeRepository.findById(recipeId).orElse(null);
        if (recipe == null) return;

        try {
            RecipeTags tags = tagExtractor.extract(recipe);

            if (tryLibraryMatch(recipe, tags)) return;
            recordNoMatch(recipe, tags);
        } catch (Exception e) {
            log.error("Image matching failed for recipe {}: {}", recipeId, e.getMessage(), e);
        }
    }

    private boolean tryLibraryMatch(Recipe recipe, RecipeTags tags) {
        LibraryMatch best = null;
        Set<String> seenUrls = new HashSet<>();
        for (ImageLibrary entry : imageLibraryRepository.findAll()) {
            if (entry.getImageUrl() == null || entry.getImageUrl().isBlank()
                    || !seenUrls.add(entry.getImageUrl())) continue;
            LibraryMatch candidate = scoreLibraryEntry(recipe.getName(), tags, entry);
            if (candidate == null || candidate.score() < libraryThreshold) continue;
            if (best == null || candidate.score() > best.score()
                    || (candidate.score() == best.score()
                    && entry.getTimesUsed() < best.entry().getTimesUsed())
                    || (candidate.score() == best.score()
                    && entry.getTimesUsed() == best.entry().getTimesUsed()
                    && candidate.titleSimilarity() > best.titleSimilarity())) {
                best = candidate;
            }
        }

        if (best == null) return false;

        recipe.setImage(best.entry().getImageUrl());
        recipeRepository.save(recipe);
        best.entry().setTimesUsed(best.entry().getTimesUsed() + 1);
        imageLibraryRepository.save(best.entry());
        logMatch(recipe, tags, ImageMatchPath.LIBRARY, best.entry().getImageUrl(), best.score());
        log.info("Recipe '{}' matched from image library (score={}, timesUsed={})",
                recipe.getName(), best.score(), best.entry().getTimesUsed());
        return true;
    }

    private LibraryMatch scoreLibraryEntry(String recipeTitle, RecipeTags tags, ImageLibrary entry) {
        double score = 0;
        double maxScore = 0;
        double titleSimilarity = titleSimilarity(recipeTitle, entry.getRecipeTitle());
        boolean exactTitleMatch = titleSimilarity == 1.0 && titleTokens(recipeTitle).size() >= 2;

        if (tags.getProtein() != null && entry.getProtein() != null
                && !sameTag(tags.getProtein(), entry.getProtein())) {
            return null;
        }

        if (tags.getDishType() != null) {
            maxScore += 3;
            if (sameTag(tags.getDishType(), entry.getDishType())) score += 3;
        }
        if (tags.getProtein() != null) {
            maxScore += 4;
            if (sameTag(tags.getProtein(), entry.getProtein())) score += 4;
        }

        List<String> recipeIngredients = normalizedIngredients(tags.getMainIngredients());
        List<String> imageIngredients = normalizedIngredients(
                entry.getIngredients() == null ? List.of() : Arrays.asList(entry.getIngredients().split(",")));
        int ingredientMatches = (int) recipeIngredients.stream()
                .filter(ingredient -> imageIngredients.stream()
                        .anyMatch(imageIngredient -> sameTag(ingredient, imageIngredient)))
                .count();
        double ingredientOverlap = recipeIngredients.isEmpty()
                ? 0
                : (double) ingredientMatches / recipeIngredients.size();
        if (!recipeIngredients.isEmpty()) {
            maxScore += 3;
            score += 3 * ingredientOverlap;
        }

        if (tags.getCookingStyle() != null) {
            maxScore += 1;
            if (sameTag(tags.getCookingStyle(), entry.getCookingStyle())) score += 1;
        }
        if (entry.getRecipeTitle() != null && !entry.getRecipeTitle().isBlank()) {
            maxScore += 3;
            score += 3 * titleSimilarity;
        }

        boolean proteinMatch = sameTag(tags.getProtein(), entry.getProtein());
        boolean dishTypeMatch = sameTag(tags.getDishType(), entry.getDishType());
        boolean strongIngredientMatch = ingredientOverlap >= 0.5;
        boolean hasDishSpecificSignal = exactTitleMatch
                || (proteinMatch && (dishTypeMatch || strongIngredientMatch))
                || (tags.getProtein() == null && dishTypeMatch && strongIngredientMatch);
        if (!hasDishSpecificSignal || maxScore == 0) return null;
        // Cuisine alone never makes a photograph a safe match for a dish.
        return new LibraryMatch(entry, score / maxScore, titleSimilarity);
    }

    private static List<String> normalizedIngredients(List<String> ingredients) {
        return ingredients.stream()
                .filter(Objects::nonNull)
                .map(RecipeImageMatchingServiceImpl::normalizeTag)
                .filter(ingredient -> !ingredient.isBlank())
                .distinct()
                .toList();
    }

    private static double titleSimilarity(String recipeTitle, String imageTitle) {
        Set<String> recipeTokens = titleTokens(recipeTitle);
        Set<String> imageTokens = titleTokens(imageTitle);
        if (recipeTokens.isEmpty() || imageTokens.isEmpty()) return 0;
        long overlap = recipeTokens.stream().filter(imageTokens::contains).count();
        return (double) overlap / Math.max(recipeTokens.size(), imageTokens.size());
    }

    private static Set<String> titleTokens(String title) {
        if (title == null) return Set.of();
        Set<String> tokens = new HashSet<>();
        for (String token : normalizeTag(title).split(" ")) {
            if (token.length() > 2 && !TITLE_STOP_WORDS.contains(token)) tokens.add(token);
        }
        return tokens;
    }

    private static boolean sameTag(String first, String second) {
        return first != null && second != null && normalizeTag(first).equals(normalizeTag(second));
    }

    private static String normalizeTag(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll("\\s+", " ");
    }

    private record LibraryMatch(ImageLibrary entry, double score, double titleSimilarity) {
    }

    private void recordNoMatch(Recipe recipe, RecipeTags tags) {
        logMatch(recipe, tags, ImageMatchPath.FALLBACK, null, null);
        log.info("No verified image found for recipe '{}'; leaving the image unset", recipe.getName());
    }

    private void logMatch(Recipe recipe, RecipeTags tags, ImageMatchPath path, String chosenUrl, Double confidence) {
        recipeImageMatchRepository.save(RecipeImageMatch.builder()
                .recipe(recipe)
                .tagsUsed(tags.describe())
                .path(path)
                .chosenImageUrl(chosenUrl)
                .confidence(confidence)
                .build());
    }
}
