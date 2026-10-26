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
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class CreatorRecipeImageServiceImpl implements CreatorRecipeImageService {

    static final String CLOUDINARY_FOLDER = "ai-recipes";
    /** Generations per photo when the provider errors; a mismatching photo is redone only once. */
    static final int MAX_ATTEMPTS = 3;
    static final String GENERIC_FOOD_IMAGE =
            "https://images.unsplash.com/photo-1495521821757-a1efb6729352?auto=format&fit=crop&w=800&q=80";

    private final RecipeGeneratedImageRepository images;
    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;
    private final AiImageGenerator generator;
    private final CloudinaryService cloudinary;
    private final VisionImageRankerService vision;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final long waitMs;
    private final long linkWaitMs;
    private final double minMatchConfidence;
    private final String placeholderUrl;
    private final String photoLinkBase;
    private final ExecutorService executor;
    /** Photos being made right now, by row id, so the photo link can wait for them. */
    private final Map<UUID, CompletableFuture<String>> inFlight = new ConcurrentHashMap<>();

    public CreatorRecipeImageServiceImpl(RecipeGeneratedImageRepository images,
                                         RecipeRepository recipeRepository,
                                         UserRepository userRepository,
                                         AiImageGenerator generator,
                                         CloudinaryService cloudinary,
                                         VisionImageRankerService vision,
                                         ObjectMapper objectMapper,
                                         @Value("${creator.ai-images.enabled:true}") boolean enabled,
                                         @Value("${creator.ai-images.wait-seconds:50}") long waitSeconds,
                                         @Value("${creator.ai-images.link-wait-seconds:85}") long linkWaitSeconds,
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
        this.linkWaitMs = TimeUnit.SECONDS.toMillis(linkWaitSeconds);
        this.minMatchConfidence = minMatchConfidence;
        this.placeholderUrl = publicBaseUrl + "/share/photo-unavailable.jpg";
        this.photoLinkBase = publicBaseUrl + "/share/ai-photo/";
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
        List<UUID> rowIds = new ArrayList<>();
        List<CompletableFuture<String>> photos = new ArrayList<>();
        for (CreateRecipeRequest recipe : recipes) {
            if (recipe == null || recipe.getName() == null || recipe.getName().isBlank()) continue;
            RecipeGeneratedImage row = newRow(RecipeImageInput.from(recipe), user.getId(), scanId, null);
            row.setFallbackImageUrl(usableImage(recipe.getImage()));
            row = images.save(row);
            targets.add(recipe);
            rowIds.add(row.getId());
            photos.add(start(row));
        }

        long deadline = System.currentTimeMillis() + waitMs;
        for (int i = 0; i < targets.size(); i++) {
            String url = null;
            try {
                long left = Math.max(0, deadline - System.currentTimeMillis());
                url = photos.get(i).get(left, TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                // Still generating: the photo link waits for it and then serves it.
                log.info("AI photo for '{}' not ready in {} ms, giving its photo link", targets.get(i).getName(), waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("AI photo for '{}' crashed: {}", targets.get(i).getName(), e.getMessage());
            }
            targets.get(i).setImage(url != null ? url : photoLinkUrl(rowIds.get(i)));
        }
    }

    @Override
    public PhotoLink photoLink(UUID id) {
        RecipeGeneratedImage row = images.findById(id).orElse(null);
        if (row == null) return null;
        if (row.getGenerationStatus() == AiImageGenerationStatus.GENERATING) {
            // Not in flight here: the server restarted mid-generation, so start it again.
            CompletableFuture<String> photo = inFlight.containsKey(id) ? inFlight.get(id) : start(row);
            try {
                String url = photo == null ? null : photo.get(linkWaitMs, TimeUnit.MILLISECONDS);
                if (url != null) return new PhotoLink(url, true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.info("AI photo {} still not ready for its link: {}", id, e.getClass().getSimpleName());
            }
            row = images.findById(id).orElse(row);
        }
        if (row.getGenerationStatus() == AiImageGenerationStatus.SUCCEEDED && row.getGeneratedImageUrl() != null) {
            return new PhotoLink(row.getGeneratedImageUrl(), true);
        }
        String fallback = usableImage(row.getFallbackImageUrl());
        return new PhotoLink(fallback != null ? fallback : GENERIC_FOOD_IMAGE, false);
    }

    /**
     * Recipes saved before photo links existed still show the "photo unavailable"
     * placeholder: give them the photo made for them since, or make a new one.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void repairPlaceholderRecipes() {
        if (!enabled) return;
        CompletableFuture.runAsync(() -> {
            try {
                for (com.cooked.backend.entity.Recipe recipe : recipeRepository.findByImage(placeholderUrl)) {
                    if (recipe.getUser() == null) continue;
                    images.findFirstByCreatorUserIdAndRecipeTitleOrderByCreatedAtDesc(recipe.getUser().getId(), recipe.getName())
                            .ifPresent(row -> repair(recipe, row));
                }
            } catch (Exception e) {
                log.warn("Could not repair recipes showing the AI photo placeholder: {}", e.getMessage());
            }
        }, executor);
    }

    private void repair(com.cooked.backend.entity.Recipe recipe, RecipeGeneratedImage row) {
        if (row.getGenerationStatus() == AiImageGenerationStatus.SUCCEEDED && row.getGeneratedImageUrl() != null) {
            recipe.setImage(row.getGeneratedImageUrl());
            recipeRepository.save(recipe);
            if (row.getRecipeId() == null) {
                row.setRecipeId(recipe.getId());
                images.save(row);
            }
            log.info("Recipe '{}' now shows its AI photo instead of the placeholder", recipe.getName());
        } else if (row.getGenerationStatus() == AiImageGenerationStatus.FAILED) {
            row.setRecipeId(recipe.getId());
            regenerate(row);
            log.info("Recipe '{}' showed the placeholder: making a new AI photo", recipe.getName());
        }
    }

    @Override
    public void linkSavedRecipe(UUID recipeId, String imageUrl) {
        if (recipeId == null || imageUrl == null || imageUrl.isBlank() || imageUrl.equals(placeholderUrl)) return;
        try {
            UUID linked = photoLinkId(imageUrl);
            if (linked != null) {
                images.findById(linked).filter(row -> row.getRecipeId() == null).ifPresent(row -> {
                    row.setRecipeId(recipeId);
                    images.save(row);
                    // Finished in the meantime: show the photo itself rather than its link.
                    if (row.getGenerationStatus() == AiImageGenerationStatus.SUCCEEDED) swapLinkOnRecipe(row);
                });
                return;
            }
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
        String previousLink = photoLinkUrl(source.getId());
        start(row).thenAccept(url -> {
            if (row.getRecipeId() == null || url == null) return;
            recipeRepository.findById(row.getRecipeId()).ifPresent(recipe -> {
                // Only replace the photo the recipe still shows (never one the creator changed).
                if (recipe.getImage() == null || recipe.getImage().equals(previousUrl)
                        || recipe.getImage().equals(previousLink) || recipe.getImage().equals(placeholderUrl)) {
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

    /** Generates [row]'s photo once, however many callers ask for it. */
    private CompletableFuture<String> start(RecipeGeneratedImage row) {
        UUID id = row.getId();
        CompletableFuture<String> photo = inFlight.computeIfAbsent(id,
                k -> CompletableFuture.supplyAsync(() -> generateInto(row), executor));
        photo.whenComplete((url, e) -> inFlight.remove(id, photo));
        return photo;
    }

    /**
     * Generates the photo for [row] and records the outcome. A provider error is
     * retried (up to {@link #MAX_ATTEMPTS} generations); a photo the vision check
     * doesn't match is redone once, and the best photo made is kept either way,
     * so a creator never ends up without one. Returns null only when no photo
     * could be made at all. Never throws.
     */
    String generateInto(RecipeGeneratedImage row) {
        long start = System.currentTimeMillis();
        RecipeImageInput input = readSnapshot(row.getRecipeSnapshot());
        Candidate best = null;
        Exception lastError = null;
        int generations = 0;
        while (generations < MAX_ATTEMPTS && (best == null || (!best.matches() && generations < 2))) {
            if (generations > 0) row.setRetried(true);
            generations++;
            try {
                Candidate candidate = attempt(row, input);
                if (best == null || candidate.score() > best.score()) best = candidate;
                if (!candidate.matches()) {
                    log.warn("AI photo for '{}' does not match the recipe (confidence {})",
                            row.getRecipeTitle(), candidate.confidence());
                }
            } catch (Exception e) {
                lastError = e;
                log.warn("AI photo for '{}' failed (attempt {}): {}", row.getRecipeTitle(), generations, e.getMessage());
            }
        }

        if (best != null) {
            row.setGeneratedImageUrl(best.url());
            row.setMatchConfidence(best.confidence());
            row.setGenerationStatus(AiImageGenerationStatus.SUCCEEDED);
            row.setFailureReason(best.matches() ? null : String.format(java.util.Locale.US,
                    "Kept the closest photo: does not fully match the recipe (confidence %.2f)", best.confidence()));
        } else {
            row.setGenerationStatus(AiImageGenerationStatus.FAILED);
            row.setFailureReason(truncate(lastError == null ? "No photo generated" : lastError.getMessage(), 500));
        }
        row.setDurationMs((int) (System.currentTimeMillis() - start));
        row.setCompletedAt(LocalDateTime.now());
        try {
            // The recipe may have been saved (and linked) while the photo was being made.
            if (row.getRecipeId() == null && row.getId() != null) {
                images.findById(row.getId()).map(RecipeGeneratedImage::getRecipeId).ifPresent(row::setRecipeId);
            }
            images.save(row);
        } catch (Exception e) {
            log.error("Could not save AI photo record for '{}': {}", row.getRecipeTitle(), e.getMessage());
        }
        if (best != null) swapLinkOnRecipe(row);
        return best != null ? best.url() : null;
    }

    /** A saved recipe still showing [row]'s photo link now shows the photo itself. */
    private void swapLinkOnRecipe(RecipeGeneratedImage row) {
        if (row.getRecipeId() == null || row.getId() == null || row.getGeneratedImageUrl() == null) return;
        try {
            String link = photoLinkUrl(row.getId());
            recipeRepository.findById(row.getRecipeId()).ifPresent(recipe -> {
                if (link.equals(recipe.getImage())) {
                    recipe.setImage(row.getGeneratedImageUrl());
                    recipeRepository.save(recipe);
                }
            });
        } catch (Exception e) {
            log.warn("Could not put AI photo on recipe {}: {}", row.getRecipeId(), e.getMessage());
        }
    }

    /** A generated photo and how well it shows the recipe (null = check could not run). */
    private record Candidate(String url, Double confidence, boolean matches) {
        double score() {
            return matches ? 2 + (confidence == null ? 0 : confidence) : (confidence == null ? 0 : confidence);
        }
    }

    /** One generation: image → Cloudinary → vision check against the recipe. */
    private Candidate attempt(RecipeGeneratedImage row, RecipeImageInput input) throws Exception {
        AiImageGenerator.GeneratedImage image = generator.generate(row.getImagePrompt());
        row.setModel(image.model());
        row.setCostUsd(round((row.getCostUsd() == null ? 0 : row.getCostUsd()) + image.costUsd()));
        String url = cloudinary.uploadBytes(image.bytes(), CLOUDINARY_FOLDER);
        if (url == null || url.isBlank()) throw new IllegalStateException("Cloudinary upload returned no URL");

        Double confidence = matchConfidence(url, input);
        return new Candidate(url, confidence, confidence == null || confidence >= minMatchConfidence);
    }

    String photoLinkUrl(UUID id) {
        return photoLinkBase + id + ".jpg";
    }

    /** The row id in a photo link, or null when [url] isn't one. */
    UUID photoLinkId(String url) {
        if (url == null || !url.startsWith(photoLinkBase) || !url.endsWith(".jpg")) return null;
        try {
            return UUID.fromString(url.substring(photoLinkBase.length(), url.length() - 4));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** [image] if it is a real photo worth falling back to, else null. */
    private String usableImage(String image) {
        if (image == null || MarkhorAiServiceImpl.isMissingImage(image) || image.equals(placeholderUrl)
                || image.startsWith(photoLinkBase)) return null;
        return image;
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
