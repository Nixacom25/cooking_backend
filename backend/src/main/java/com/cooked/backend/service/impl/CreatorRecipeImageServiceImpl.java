package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.cooked.backend.entity.AiImageApprovalStatus;
import com.cooked.backend.entity.AiImageGenerationStatus;
import com.cooked.backend.entity.RecipeGeneratedImage;
import com.cooked.backend.entity.Role;
import com.cooked.backend.entity.User;
import com.cooked.backend.repository.RecipeGeneratedImageRepository;
import com.cooked.backend.repository.RecipeRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.AiImageGenerator;
import com.cooked.backend.service.CloudinaryService;
import com.cooked.backend.service.CreatorRecipeImageService;
import com.cooked.backend.service.RecipeImagePromptBuilder;
import com.cooked.backend.service.VisionImageRankerService;
import com.cooked.backend.service.dto.RecipeImageInput;
import com.cooked.backend.service.dto.VisionRankResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class CreatorRecipeImageServiceImpl implements CreatorRecipeImageService {

    static final String CLOUDINARY_FOLDER = "ai-recipes";

    private final RecipeGeneratedImageRepository images;
    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;
    private final AiImageGenerator generator;
    private final CloudinaryService cloudinary;
    private final VisionImageRankerService vision;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final long waitMs;
    private final double minMatchConfidence;
    private final String placeholderUrl;
    private final ExecutorService executor;

    public CreatorRecipeImageServiceImpl(RecipeGeneratedImageRepository images,
                                         RecipeRepository recipeRepository,
                                         UserRepository userRepository,
                                         AiImageGenerator generator,
                                         CloudinaryService cloudinary,
                                         VisionImageRankerService vision,
                                         ObjectMapper objectMapper,
                                         @Value("${creator.ai-images.enabled:true}") boolean enabled,
                                         @Value("${creator.ai-images.wait-seconds:50}") long waitSeconds,
                                         @Value("${creator.ai-images.min-match-confidence:0.5}") double minMatchConfidence,
                                         @Value("${creator.ai-images.parallelism:8}") int parallelism,
                                         @Value("${app.public-base-url:https://link.cookedapp.com}") String publicBaseUrl) {
        this.images = images;
        this.recipeRepository = recipeRepository;
        this.userRepository = userRepository;
        this.generator = generator;
        this.cloudinary = cloudinary;
        this.vision = vision;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.waitMs = TimeUnit.SECONDS.toMillis(waitSeconds);
        this.minMatchConfidence = minMatchConfidence;
        this.placeholderUrl = publicBaseUrl + "/share/photo-unavailable.jpg";
        AtomicInteger n = new AtomicInteger();
        this.executor = Executors.newFixedThreadPool(Math.max(1, parallelism), r -> {
            Thread t = new Thread(r, "ai-recipe-image-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    @Override
    public boolean isEnabledFor(User user) {
        return enabled && user != null && user.getRole() == Role.CREATOR;
    }

    @Override
    public String placeholderUrl() {
        return placeholderUrl;
    }

    @Override
    public void attachImages(List<CreateRecipeRequest> recipes, String userEmail) {
        if (recipes == null || recipes.isEmpty() || !enabled || userEmail == null) return;
        User user = userRepository.findByEmail(userEmail).orElse(null);
        if (!isEnabledFor(user)) return;

        UUID scanId = UUID.randomUUID();
        List<CreateRecipeRequest> targets = new ArrayList<>();
        List<CompletableFuture<String>> photos = new ArrayList<>();
        for (CreateRecipeRequest recipe : recipes) {
            if (recipe == null || recipe.getName() == null || recipe.getName().isBlank()) continue;
            RecipeGeneratedImage row = images.save(newRow(RecipeImageInput.from(recipe), user.getId(), scanId, null));
            targets.add(recipe);
            photos.add(CompletableFuture.supplyAsync(() -> generateInto(row), executor));
        }

        long deadline = System.currentTimeMillis() + waitMs;
        for (int i = 0; i < targets.size(); i++) {
            String url;
            try {
                long left = Math.max(0, deadline - System.currentTimeMillis());
                url = photos.get(i).get(left, TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                // Still generating: it finishes in the background and stays reviewable.
                log.info("AI photo for '{}' not ready in {} ms, showing the placeholder", targets.get(i).getName(), waitMs);
                url = placeholderUrl;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                url = placeholderUrl;
            } catch (Exception e) {
                url = placeholderUrl;
            }
            targets.get(i).setImage(url);
        }
    }

    @Override
    public void linkSavedRecipe(UUID recipeId, String imageUrl) {
        if (recipeId == null || imageUrl == null || imageUrl.isBlank() || imageUrl.equals(placeholderUrl)) return;
        try {
            images.findFirstByGeneratedImageUrlAndRecipeIdIsNull(imageUrl).ifPresent(row -> {
                row.setRecipeId(recipeId);
                images.save(row);
            });
        } catch (Exception e) {
            log.warn("Could not link AI photo to recipe {}: {}", recipeId, e.getMessage());
        }
    }

    @Override
    public RecipeGeneratedImage regenerate(RecipeGeneratedImage source) {
        RecipeImageInput input = readSnapshot(source.getRecipeSnapshot());
        RecipeGeneratedImage row = images.save(newRow(input, source.getCreatorUserId(), source.getScanId(), source.getId()));
        row.setRecipeId(source.getRecipeId());
        images.save(row);

        source.setApprovalStatus(AiImageApprovalStatus.NEEDS_REGENERATION);
        images.save(source);

        String previousUrl = source.getGeneratedImageUrl();
        CompletableFuture.supplyAsync(() -> generateInto(row), executor).thenAccept(url -> {
            if (row.getRecipeId() == null || url.equals(placeholderUrl)) return;
            recipeRepository.findById(row.getRecipeId()).ifPresent(recipe -> {
                // Only replace the photo the recipe still shows (never one the creator changed).
                if (recipe.getImage() == null || recipe.getImage().equals(previousUrl)
                        || recipe.getImage().equals(placeholderUrl)) {
                    recipe.setImage(url);
                    recipeRepository.save(recipe);
                }
            });
        });
        return row;
    }

    private RecipeGeneratedImage newRow(RecipeImageInput input, UUID creatorId, UUID scanId, UUID regeneratedFrom) {
        String snapshot = writeSnapshot(input);
        return RecipeGeneratedImage.builder()
                .recipeTitle(truncate(input.title(), 255))
                .recipeSnapshot(snapshot)
                .recipeVersion(shortHash(snapshot))
                .imagePrompt(RecipeImagePromptBuilder.build(input))
                .imageProvider(generator.provider())
                .model(generator.model())
                .quality(generator.quality())
                .generationStatus(AiImageGenerationStatus.GENERATING)
                .creatorUserId(creatorId)
                .scanId(scanId)
                .regeneratedFromId(regeneratedFrom)
                .costUsd(0.0)
                .build();
    }

    /** Generates the photo for [row] (one retry) and records the outcome. Never throws. */
    String generateInto(RecipeGeneratedImage row) {
        long start = System.currentTimeMillis();
        RecipeImageInput input = readSnapshot(row.getRecipeSnapshot());
        String url = null;
        try {
            try {
                url = attempt(row, input);
            } catch (Exception first) {
                log.warn("AI photo for '{}' failed, retrying: {}", row.getRecipeTitle(), first.getMessage());
                row.setRetried(true);
                url = attempt(row, input);
            }
            row.setGenerationStatus(AiImageGenerationStatus.SUCCEEDED);
            row.setFailureReason(null);
        } catch (Exception e) {
            log.warn("AI photo for '{}' failed after retry: {}", row.getRecipeTitle(), e.getMessage());
            row.setGenerationStatus(AiImageGenerationStatus.FAILED);
            row.setFailureReason(truncate(e.getMessage(), 500));
            url = null;
        }
        row.setDurationMs((int) (System.currentTimeMillis() - start));
        row.setCompletedAt(LocalDateTime.now());
        try {
            images.save(row);
        } catch (Exception e) {
            log.error("Could not save AI photo record for '{}': {}", row.getRecipeTitle(), e.getMessage());
        }
        return url != null ? url : placeholderUrl;
    }

    /** One generation: image → Cloudinary → vision check against the recipe. */
    private String attempt(RecipeGeneratedImage row, RecipeImageInput input) throws Exception {
        AiImageGenerator.GeneratedImage image = generator.generate(row.getImagePrompt());
        row.setModel(image.model());
        row.setCostUsd(round((row.getCostUsd() == null ? 0 : row.getCostUsd()) + image.costUsd()));
        String url = cloudinary.uploadBytes(image.bytes(), CLOUDINARY_FOLDER);
        row.setGeneratedImageUrl(url);

        Double confidence = matchConfidence(url, input);
        row.setMatchConfidence(confidence);
        if (confidence != null && confidence < minMatchConfidence) {
            throw new IllegalStateException(String.format(java.util.Locale.US,
                    "Photo does not match the recipe (confidence %.2f)", confidence));
        }
        return url;
    }

    /** How well the photo shows this exact dish, or null when the check could not run. */
    private Double matchConfidence(String url, RecipeImageInput input) {
        try {
            String dish = input.title() + " (made with " + String.join(", ", input.ingredientNames()) + ")";
            VisionRankResult result = vision.rankCandidates(dish, List.of(url));
            if (result == null || "Vision call failed".equals(result.getReason())) return null;
            return result.getBestIndex() == 0 ? result.getConfidence() : 0.0;
        } catch (Exception e) {
            return null;
        }
    }

    private String writeSnapshot(RecipeImageInput input) {
        try {
            return objectMapper.writeValueAsString(input);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize recipe for AI photo", e);
        }
    }

    RecipeImageInput readSnapshot(String json) {
        try {
            return objectMapper.readValue(json, RecipeImageInput.class);
        } catch (Exception e) {
            throw new IllegalStateException("Unreadable recipe snapshot", e);
        }
    }

    private static String shortHash(String s) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (Exception e) {
            return null;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
