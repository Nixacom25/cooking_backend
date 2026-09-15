package com.cooked.backend.service.impl;

import com.cooked.backend.entity.ImageLibrary;
import com.cooked.backend.entity.ImageSource;
import com.cooked.backend.entity.Recipe;
import com.cooked.backend.repository.ImageLibraryRepository;
import com.cooked.backend.repository.RecipeRepository;
import com.cooked.backend.service.ImageLibraryBackfillService;
import com.cooked.backend.service.VisionImageRankerService;
import com.cooked.backend.service.dto.RecipeTags;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ImageLibraryBackfillServiceImpl implements ImageLibraryBackfillService {

    private static final Logger log = LoggerFactory.getLogger(ImageLibraryBackfillServiceImpl.class);

    private final RecipeRepository recipeRepository;
    private final ImageLibraryRepository imageLibraryRepository;
    private final VisionImageRankerService visionRanker;

    @Override
    @Transactional
    public int backfillFromExistingRecipes() {
        Set<String> seen = new HashSet<>();
        int created = 0;

        for (Recipe recipe : recipeRepository.findAll()) {
            String imageUrl = recipe.getImage();
            if (imageUrl == null || imageUrl.isBlank()) continue;
            if (!seen.add(imageUrl)) continue; // already tagged this run
            if (imageLibraryRepository.existsByImageUrl(imageUrl)) continue; // already tagged in a previous run

            try {
                RecipeTags tags = visionRanker.tagImage(imageUrl, recipe.getName());
                imageLibraryRepository.save(ImageLibrary.builder()
                        .imageUrl(imageUrl)
                        .cuisine(tags.getCuisine())
                        .protein(tags.getProtein())
                        .dishType(tags.getDishType())
                        .cookingStyle(tags.getCookingStyle())
                        .ingredients(String.join(",", tags.getMainIngredients()))
                        .source(ImageSource.BACKFILL)
                        .build());
                created++;
            } catch (Exception e) {
                log.warn("Backfill tagging failed for image '{}': {}", imageUrl, e.getMessage());
            }
        }

        log.info("Image library backfill complete: {} new entries", created);
        return created;
    }
}
