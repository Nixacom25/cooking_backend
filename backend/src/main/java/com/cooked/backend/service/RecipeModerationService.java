package com.cooked.backend.service;

import com.cooked.backend.dto.request.RecipeFlagRequest;
import com.cooked.backend.dto.response.RecipeFlagResponse;
import com.cooked.backend.dto.response.RecipeMergeResponse;

import java.util.List;
import java.util.UUID;

/** Recipe reports and duplicate merging (admin). */
public interface RecipeModerationService {

    List<RecipeFlagResponse> flags(UUID recipeId);

    RecipeFlagResponse flag(UUID recipeId, RecipeFlagRequest request, String adminEmail);

    RecipeFlagResponse resolve(UUID flagId, String adminEmail);

    /**
     * Keeps [keepId]: cookbooks, meal plans and grocery items of [duplicateId] now point to it,
     * then the duplicate is archived (soft delete, restorable).
     */
    RecipeMergeResponse merge(UUID keepId, UUID duplicateId, String adminEmail);
}
