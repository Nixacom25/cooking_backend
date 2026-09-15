package com.cooked.backend.service;

import com.cooked.backend.service.dto.RecipeTags;
import com.cooked.backend.service.dto.VisionRankResult;

import java.util.List;

/**
 * Uses a vision model for two things: (1) picking the best candidate photo
 * for a recipe out of a short list, with a confidence score, and (2) tagging
 * an existing image with structured metadata for the image library backfill.
 */
public interface VisionImageRankerService {

    VisionRankResult rankCandidates(String recipeTitle, List<String> candidateImageUrls);

    RecipeTags tagImage(String imageUrl, String recipeContextName);
}
