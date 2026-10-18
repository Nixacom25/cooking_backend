package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.AiRecipeImageReviewRequest;
import com.cooked.backend.dto.response.AiRecipeImageResponse;
import com.cooked.backend.dto.response.AiRecipeImageStatsResponse;
import com.cooked.backend.dto.response.AiRecipeImageTags;
import com.cooked.backend.entity.AiImageApprovalStatus;
import com.cooked.backend.entity.AiImageGenerationStatus;
import com.cooked.backend.entity.ImageLibrary;
import com.cooked.backend.entity.ImageSource;
import com.cooked.backend.entity.RecipeGeneratedImage;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.ImageLibraryRepository;
import com.cooked.backend.repository.RecipeGeneratedImageRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.AiRecipeImageReviewService;
import com.cooked.backend.service.CreatorRecipeImageService;
import com.cooked.backend.service.RecipeTagExtractor;
import com.cooked.backend.service.dto.RecipeImageInput;
import com.cooked.backend.service.dto.RecipeTags;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiRecipeImageReviewServiceImpl implements AiRecipeImageReviewService {

    private final RecipeGeneratedImageRepository images;
    private final ImageLibraryRepository library;
    private final UserRepository users;
    private final CreatorRecipeImageService creatorImages;
    private final RecipeTagExtractor tagExtractor;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<AiRecipeImageResponse> list(String filter, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)));
        String f = filter == null ? "pending" : filter.trim().toLowerCase(Locale.ROOT);
        Page<RecipeGeneratedImage> rows = switch (f) {
            case "all" -> images.findAllByOrderByCreatedAtDesc(pageable);
            case "failed" -> images.findByGenerationStatusOrderByCreatedAtDesc(AiImageGenerationStatus.FAILED, pageable);
            case "generating" -> images.findByGenerationStatusOrderByCreatedAtDesc(AiImageGenerationStatus.GENERATING, pageable);
            case "approved" -> images.findByApprovalStatusOrderByCreatedAtDesc(AiImageApprovalStatus.APPROVED, pageable);
            case "rejected" -> images.findByApprovalStatusOrderByCreatedAtDesc(AiImageApprovalStatus.REJECTED, pageable);
            case "needs_regeneration" -> images.findByApprovalStatusOrderByCreatedAtDesc(AiImageApprovalStatus.NEEDS_REGENERATION, pageable);
            default -> images.findByApprovalStatusOrderByCreatedAtDesc(AiImageApprovalStatus.PENDING_REVIEW, pageable);
        };
        Map<UUID, User> creators = users.findAllById(rows.stream()
                        .map(RecipeGeneratedImage::getCreatorUserId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return rows.map(row -> toResponse(row, creators.get(row.getCreatorUserId())));
    }

    @Override
    @Transactional(readOnly = true)
    public AiRecipeImageStatsResponse stats(int days) {
        int d = Math.max(1, Math.min(days, 365));
        LocalDateTime since = LocalDateTime.now().minusDays(d);

        List<AiRecipeImageStatsResponse.ModelStats> models = new ArrayList<>();
        long started = 0, succeeded = 0, failed = 0, retried = 0, approved = 0, rejected = 0, regenerated = 0, added = 0;
        double cost = 0, durationWeighted = 0;
        for (Object[] r : images.statsByModel(since)) {
            long total = num(r[1]), ok = num(r[2]), ko = num(r[3]), retry = num(r[4]),
                    yes = num(r[5]), no = num(r[6]), regen = num(r[7]), lib = num(r[8]);
            Double avg = r[9] == null ? null : ((Number) r[9]).doubleValue();
            double modelCost = r[10] == null ? 0 : ((Number) r[10]).doubleValue();
            models.add(new AiRecipeImageStatsResponse.ModelStats(String.valueOf(r[0]), total, ok, ko, retry,
                    yes, no, round(avg), round4(modelCost), pct(yes, yes + no)));
            started += total; succeeded += ok; failed += ko; retried += retry;
            approved += yes; rejected += no; regenerated += regen; added += lib;
            cost += modelCost;
            if (avg != null) durationWeighted += avg * ok;
        }

        Map<String, Long> events = new LinkedHashMap<>();
        events.put("recipe_image_generation_started", started);
        events.put("recipe_image_generation_completed", succeeded);
        events.put("recipe_image_generation_failed", failed);
        events.put("recipe_image_generation_retried", retried);
        events.put("recipe_image_admin_approved", approved);
        events.put("recipe_image_admin_rejected", rejected);
        events.put("recipe_image_regenerated", regenerated);
        events.put("recipe_image_added_to_database", added);

        Map<String, Long> reasons = new LinkedHashMap<>();
        for (Object[] r : images.rejectionReasons(since)) {
            reasons.put(r[0] == null ? "OTHER" : String.valueOf(r[0]), num(r[1]));
        }

        return new AiRecipeImageStatsResponse(d, events,
                images.countByApprovalStatus(AiImageApprovalStatus.PENDING_REVIEW),
                succeeded == 0 ? null : round(durationWeighted / succeeded),
                round4(cost),
                started == 0 ? null : round4(cost / started),
                pct(failed, started), pct(retried, started),
                pct(approved, approved + rejected), pct(rejected, approved + rejected),
                reasons, models);
    }

    @Override
    @Transactional
    public AiRecipeImageResponse approve(UUID id, AiRecipeImageReviewRequest request, String adminEmail) {
        RecipeGeneratedImage row = find(id);
        if (row.getGenerationStatus() != AiImageGenerationStatus.SUCCEEDED || row.getGeneratedImageUrl() == null) {
            throw new BadRequestException("Only a successfully generated photo can be approved.");
        }
        RecipeImageInput input = snapshot(row);
        AiRecipeImageTags tags = merge(request == null ? null : request.tags(), suggestedTags(input));
        if (tags.qualityScore() != null && (tags.qualityScore() < 1 || tags.qualityScore() > 5)) {
            throw new BadRequestException("Quality score must be between 1 and 5.");
        }

        ImageLibrary entry = row.getImageLibraryId() == null ? null : library.findById(row.getImageLibraryId()).orElse(null);
        if (entry == null) {
            entry = ImageLibrary.builder().imageUrl(row.getGeneratedImageUrl()).source(ImageSource.AI_GENERATED).build();
        }
        entry.setRecipeTitle(truncate(row.getRecipeTitle(), 255));
        entry.setDishType(truncate(lower(tags.dishType()), 150));
        entry.setCuisine(truncate(lower(tags.cuisine()), 100));
        entry.setProtein(truncate(lower(tags.protein()), 100));
        entry.setCookingStyle(truncate(lower(tags.cookingMethod()), 100));
        entry.setIngredients(joinLower(tags.mainIngredients() == null || tags.mainIngredients().isEmpty()
                ? input.ingredientNames() : tags.mainIngredients()));
        entry.setMealType(truncate(lower(tags.mealType()), 50));
        entry.setDietaryTags(truncate(joinLower(tags.dietaryTags()), 255));
        entry.setVisualStyle(truncate(lower(tags.visualStyle()), 50));
        entry.setQualityScore(tags.qualityScore());
        entry = library.save(entry);

        row.setImageLibraryId(entry.getId());
        row.setApprovalStatus(AiImageApprovalStatus.APPROVED);
        row.setRejectionReason(null);
        markReviewed(row, adminEmail);
        return toResponse(images.save(row));
    }

    @Override
    @Transactional
    public AiRecipeImageResponse reject(UUID id, AiRecipeImageReviewRequest request, String adminEmail) {
        String reason = request == null || request.reason() == null ? "" : request.reason().trim().toUpperCase(Locale.ROOT);
        if (!REJECTION_REASONS.contains(reason)) {
            throw new BadRequestException("Pick a rejection reason: " + String.join(", ", REJECTION_REASONS));
        }
        RecipeGeneratedImage row = find(id);
        if (row.getImageLibraryId() != null) {
            // Rejected after approval: future recipes must not reuse it any more.
            library.deleteById(row.getImageLibraryId());
            row.setImageLibraryId(null);
        }
        row.setApprovalStatus(AiImageApprovalStatus.REJECTED);
        row.setRejectionReason(reason);
        markReviewed(row, adminEmail);
        return toResponse(images.save(row));
    }

    @Override
    public AiRecipeImageResponse regenerate(UUID id, String adminEmail) {
        RecipeGeneratedImage source = find(id);
        if (source.getGenerationStatus() == AiImageGenerationStatus.GENERATING) {
            throw new BadRequestException("This photo is still being generated.");
        }
        markReviewed(source, adminEmail);
        return toResponse(creatorImages.regenerate(source));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private RecipeGeneratedImage find(UUID id) {
        return images.findById(id).orElseThrow(() -> new ResourceNotFoundException("AI photo not found"));
    }

    private void markReviewed(RecipeGeneratedImage row, String adminEmail) {
        row.setReviewedBy(truncate(adminEmail, 160));
        row.setReviewedAt(LocalDateTime.now());
    }

    private AiRecipeImageResponse toResponse(RecipeGeneratedImage row) {
        User creator = row.getCreatorUserId() == null ? null : users.findById(row.getCreatorUserId()).orElse(null);
        return toResponse(row, creator);
    }

    private AiRecipeImageResponse toResponse(RecipeGeneratedImage row, User creator) {
        RecipeImageInput input = snapshot(row);
        List<String> ingredients = input.ingredients() == null ? List.of() : input.ingredients().stream()
                .map(i -> i.quantity() == null || i.quantity().isBlank() || i.quantity().equals("-")
                        ? i.name() : i.name() + " (" + i.quantity() + ")")
                .toList();
        String creatorName = creator == null ? null
                : (Objects.toString(creator.getFirstname(), "") + " " + Objects.toString(creator.getLastname(), "")).trim();
        return new AiRecipeImageResponse(
                row.getId(), row.getRecipeId(), row.getRecipeTitle(), input.cuisine(),
                input.dishTypes() == null ? List.of() : input.dishTypes(), ingredients,
                row.getGeneratedImageUrl(), row.getImagePrompt(), row.getImageProvider(), row.getModel(), row.getQuality(),
                row.getGenerationStatus().name(), row.isRetried(), row.getFailureReason(), row.getMatchConfidence(),
                row.getDurationMs(), row.getCostUsd(),
                row.getCreatorUserId(), creatorName == null || creatorName.isEmpty() ? null : creatorName,
                creator == null ? null : creator.getEmail(),
                row.getScanId(), row.getRecipeVersion(), row.getApprovalStatus().name(), row.getRejectionReason(),
                row.getReviewedBy(), row.getReviewedAt(), row.getRegeneratedFromId(), row.getImageLibraryId() != null,
                row.getCreatedAt(), row.getCompletedAt(), suggestedTags(input));
    }

    AiRecipeImageTags suggestedTags(RecipeImageInput input) {
        String dishType = input.dishTypes() == null || input.dishTypes().isEmpty() ? null : input.dishTypes().get(0);
        RecipeTags tags = tagExtractor.extract(input.title(), input.ingredientNames(), input.cuisine(), dishType);
        return new AiRecipeImageTags(tags.getDishType(), tags.getCuisine(), tags.getProtein(),
                tags.getMainIngredients(), null, tags.getCookingStyle(), List.of(), "editorial", null);
    }

    /** Admin values win; blank ones fall back to the suggestion. */
    static AiRecipeImageTags merge(AiRecipeImageTags given, AiRecipeImageTags suggested) {
        if (given == null) return suggested;
        return new AiRecipeImageTags(
                or(given.dishType(), suggested.dishType()),
                or(given.cuisine(), suggested.cuisine()),
                or(given.protein(), suggested.protein()),
                given.mainIngredients() == null || given.mainIngredients().isEmpty() ? suggested.mainIngredients() : given.mainIngredients(),
                or(given.mealType(), suggested.mealType()),
                or(given.cookingMethod(), suggested.cookingMethod()),
                given.dietaryTags() == null ? suggested.dietaryTags() : given.dietaryTags(),
                or(given.visualStyle(), suggested.visualStyle()),
                given.qualityScore() != null ? given.qualityScore() : suggested.qualityScore());
    }

    private RecipeImageInput snapshot(RecipeGeneratedImage row) {
        try {
            return objectMapper.readValue(row.getRecipeSnapshot(), RecipeImageInput.class);
        } catch (Exception e) {
            return new RecipeImageInput(row.getRecipeTitle(), null, List.of(), List.of(), List.of());
        }
    }

    private static String or(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String lower(String s) {
        return s == null || s.isBlank() ? null : s.trim().toLowerCase(Locale.ROOT);
    }

    private static String joinLower(List<String> values) {
        if (values == null) return null;
        String joined = values.stream().filter(v -> v != null && !v.isBlank())
                .map(v -> v.trim().toLowerCase(Locale.ROOT)).distinct().collect(Collectors.joining(","));
        return joined.isEmpty() ? null : joined;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static long num(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }

    private static Double pct(long part, long whole) {
        return whole == 0 ? null : Math.round(1000.0 * part / whole) / 10.0;
    }

    private static Double round(Double v) {
        return v == null ? null : (double) Math.round(v);
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
