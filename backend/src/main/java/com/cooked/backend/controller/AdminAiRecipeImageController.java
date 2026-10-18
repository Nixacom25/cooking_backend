package com.cooked.backend.controller;

import com.cooked.backend.dto.request.AiRecipeImageReviewRequest;
import com.cooked.backend.dto.response.AiRecipeImageResponse;
import com.cooked.backend.dto.response.AiRecipeImageStatsResponse;
import com.cooked.backend.service.AiRecipeImageReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Review of creator AI recipe photos. HTTP only: the work is in {@link AiRecipeImageReviewService}. */
@RestController
@RequestMapping("/api/admin/ai-images")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin AI recipe photos", description = "Review, approve, reject and regenerate creator AI recipe photos")
public class AdminAiRecipeImageController {

    private final AiRecipeImageReviewService reviews;

    @Operation(summary = "Photos to review (filter: pending | approved | rejected | needs_regeneration | failed | generating | all)")
    @GetMapping
    public ResponseEntity<Page<AiRecipeImageResponse>> list(@RequestParam(defaultValue = "pending") String filter,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "24") int size) {
        return ResponseEntity.ok(reviews.list(filter, page, size));
    }

    @Operation(summary = "Generation, cost and review metrics (days: 1-365, default 30)")
    @GetMapping("/stats")
    public ResponseEntity<AiRecipeImageStatsResponse> stats(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(reviews.stats(days));
    }

    @Operation(summary = "Rejection reasons an admin can pick")
    @GetMapping("/rejection-reasons")
    public ResponseEntity<List<String>> rejectionReasons() {
        return ResponseEntity.ok(AiRecipeImageReviewService.REJECTION_REASONS);
    }

    @Operation(summary = "Approve: add the photo to the Cooked photo library with structured tags")
    @PostMapping("/{id}/approve")
    public ResponseEntity<AiRecipeImageResponse> approve(@PathVariable UUID id,
                                                         @RequestBody(required = false) AiRecipeImageReviewRequest request,
                                                         Authentication auth) {
        return ResponseEntity.ok(reviews.approve(id, request, auth.getName()));
    }

    @Operation(summary = "Reject with a reason (kept on its recipe, out of the library)")
    @PostMapping("/{id}/reject")
    public ResponseEntity<AiRecipeImageResponse> reject(@PathVariable UUID id,
                                                        @RequestBody AiRecipeImageReviewRequest request,
                                                        Authentication auth) {
        return ResponseEntity.ok(reviews.reject(id, request, auth.getName()));
    }

    @Operation(summary = "Regenerate from the same recipe data (runs in the background)")
    @PostMapping("/{id}/regenerate")
    public ResponseEntity<AiRecipeImageResponse> regenerate(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(reviews.regenerate(id, auth.getName()));
    }
}
