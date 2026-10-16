package com.cooked.backend.service;

import com.cooked.backend.dto.request.CreateCookbookRequest;
import com.cooked.backend.dto.response.CookbookResponse;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.dto.response.SharedCookbookPreviewResponse;

import java.util.List;
import java.util.UUID;

public interface CookbookService {
    CookbookResponse create(String userEmail, CreateCookbookRequest request);

    CookbookResponse update(UUID id, String userEmail, CreateCookbookRequest request);

    List<CookbookResponse> getMyCookbooks(String userEmail);

    CookbookResponse getCookbook(UUID id, String userEmail);

    MessageResponse delete(UUID id, String userEmail);

    CookbookResponse togglePin(UUID id, String userEmail);

    /** Owner only: marks the cookbook as shared and returns its public link. */
    String getShareLink(UUID id, String userEmail);

    /** Any signed-in user holding the link of a shared cookbook. */
    SharedCookbookPreviewResponse getSharedPreview(UUID id, String userEmail);

    /** Gives the user their own copy of a shared cookbook and of its recipes. */
    CookbookResponse importShared(UUID id, String userEmail);
}
