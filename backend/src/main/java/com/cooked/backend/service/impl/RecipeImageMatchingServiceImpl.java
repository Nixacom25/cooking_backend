package com.cooked.backend.service.impl;

import com.cooked.backend.entity.*;
import com.cooked.backend.repository.ImageLibraryRepository;
import com.cooked.backend.repository.RecipeImageMatchRepository;
import com.cooked.backend.repository.RecipeRepository;
import com.cooked.backend.service.*;
import com.cooked.backend.service.dto.RecipeTags;
import com.cooked.backend.service.dto.VisionRankResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecipeImageMatchingServiceImpl implements RecipeImageMatchingService {

    private static final Logger log = LoggerFactory.getLogger(RecipeImageMatchingServiceImpl.class);
    private static final int CANDIDATE_LIMIT = 8;

    private final RecipeRepository recipeRepository;
    private final ImageLibraryRepository imageLibraryRepository;
    private final RecipeImageMatchRepository recipeImageMatchRepository;
    private final RecipeTagExtractor tagExtractor;
    private final ImageCandidateProvider candidateProvider;
    private final VisionImageRankerService visionRanker;
    private final CloudinaryService cloudinaryService;
    private final TaxonomyService taxonomyService;

    @Value("${image.matching.library.threshold:0.75}")
    private double libraryThreshold;

    @Value("${image.matching.vision.threshold:0.75}")
    private double visionThreshold;

    @Override
    @Async
    @Transactional
    public void matchAndAssignImageAsync(UUID recipeId) {
        Recipe recipe = recipeRepository.findById(recipeId).orElse(null);
        if (recipe == null) return;

        try {
            RecipeTags tags = tagExtractor.extract(recipe);

            if (tryLibraryMatch(recipe, tags)) return;
            if (tryExternalMatch(recipe, tags)) return;
            tryGenericFallback(recipe, tags);
        } catch (Exception e) {
            log.error("Image matching failed for recipe {}: {}", recipeId, e.getMessage(), e);
        }
    }

    private boolean tryLibraryMatch(Recipe recipe, RecipeTags tags) {
        ImageLibrary best = null;
        double bestScore = 0;
        for (ImageLibrary entry : imageLibraryRepository.findAll()) {
            double score = scoreLibraryEntry(tags, entry);
            if (score > bestScore) {
                bestScore = score;
                best = entry;
            }
        }

        if (best == null || bestScore < libraryThreshold) return false;

        recipe.setImage(best.getImageUrl());
        recipeRepository.save(recipe);
        best.setTimesUsed(best.getTimesUsed() + 1);
        imageLibraryRepository.save(best);
        logMatch(recipe, tags, ImageMatchPath.LIBRARY, best.getImageUrl(), bestScore);
        log.info("Recipe '{}' matched from image library (score={})", recipe.getName(), bestScore);
        return true;
    }

    private double scoreLibraryEntry(RecipeTags tags, ImageLibrary entry) {
        double score = 0;
        double maxScore = 0;

        if (tags.getDishType() != null) {
            maxScore += 3;
            if (tags.getDishType().equalsIgnoreCase(entry.getDishType())) score += 3;
        }
        if (tags.getProtein() != null) {
            maxScore += 3;
            if (tags.getProtein().equalsIgnoreCase(entry.getProtein())) score += 3;
        }
        if (tags.getCuisine() != null) {
            maxScore += 2;
            if (tags.getCuisine().equalsIgnoreCase(entry.getCuisine())) score += 2;
        }
        if (tags.getCookingStyle() != null) {
            maxScore += 1;
            if (tags.getCookingStyle().equalsIgnoreCase(entry.getCookingStyle())) score += 1;
        }
        List<String> entryIngredients = entry.getIngredients() == null
                ? List.of()
                : Arrays.asList(entry.getIngredients().split(","));
        for (String ing : tags.getMainIngredients()) {
            maxScore += 0.5;
            if (entryIngredients.stream().anyMatch(e -> e.trim().equalsIgnoreCase(ing.trim()))) score += 0.5;
        }

        // Not enough signal present to trust reusing this image at all.
        if (maxScore < 2) return 0;
        return score / maxScore;
    }

    private boolean tryExternalMatch(Recipe recipe, RecipeTags tags) {
        if (attemptSearchAndRank(recipe, tags)) return true;
        // One retry with broadened tags (drop cooking style) before giving up.
        RecipeTags broadened = tagExtractor.broaden(tags);
        return attemptSearchAndRank(recipe, broadened);
    }

    private boolean attemptSearchAndRank(Recipe recipe, RecipeTags tags) {
        List<String> candidates = candidateProvider.searchCandidates(tags.searchQuery(), CANDIDATE_LIMIT);
        if (candidates.isEmpty()) return false;

        VisionRankResult result = visionRanker.rankCandidates(recipe.getName(), candidates);
        if (result.getBestIndex() < 0 || result.getBestIndex() >= candidates.size()
                || result.getConfidence() < visionThreshold) {
            return false;
        }

        String sourceUrl = candidates.get(result.getBestIndex());
        String hostedUrl;
        try {
            hostedUrl = cloudinaryService.uploadUrl(sourceUrl);
        } catch (Exception e) {
            log.warn("Cloudinary upload failed, using source URL directly: {}", e.getMessage());
            hostedUrl = sourceUrl;
        }

        recipe.setImage(hostedUrl);
        recipeRepository.save(recipe);

        imageLibraryRepository.save(ImageLibrary.builder()
                .imageUrl(hostedUrl)
                .cuisine(tags.getCuisine())
                .protein(tags.getProtein())
                .dishType(tags.getDishType())
                .cookingStyle(tags.getCookingStyle())
                .ingredients(String.join(",", tags.getMainIngredients()))
                .source(ImageSource.EDAMAM)
                .build());

        logMatch(recipe, tags, ImageMatchPath.EDAMAM, hostedUrl, result.getConfidence());
        log.info("Recipe '{}' matched via Edamam+Vision (confidence={})", recipe.getName(), result.getConfidence());
        return true;
    }

    private void tryGenericFallback(Recipe recipe, RecipeTags tags) {
        String image = null;

        if (tags.getDishType() != null) {
            image = taxonomyService.getCategoryImages().get(tags.getDishType());
        }
        if (image == null && tags.getCuisine() != null) {
            image = taxonomyService.getCuisineImages().get(tags.getCuisine());
        }
        if (image == null && tags.getCuisine() != null) {
            Page<Recipe> byCuisine = recipeRepository.findByCuisineWithImage(tags.getCuisine(), PageRequest.of(0, 1));
            if (byCuisine.hasContent()) image = byCuisine.getContent().get(0).getImage();
        }
        if (image == null && tags.getDishType() != null) {
            Page<Recipe> byCategory = recipeRepository.findByCategoryWithImage(tags.getDishType(), PageRequest.of(0, 1));
            if (byCategory.hasContent()) image = byCategory.getContent().get(0).getImage();
        }

        if (image != null) {
            recipe.setImage(image);
            recipeRepository.save(recipe);
        }
        logMatch(recipe, tags, ImageMatchPath.FALLBACK, image, null);
        log.info("Recipe '{}' fell back to generic image (found={})", recipe.getName(), image != null);
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
