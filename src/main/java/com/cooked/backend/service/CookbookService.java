package com.cooked.backend.service;

import com.cooked.backend.dto.request.CreateCookbookRequest;
import com.cooked.backend.dto.response.CookbookResponse;
import com.cooked.backend.dto.response.MessageResponse;

import java.util.List;
import java.util.UUID;

public interface CookbookService {
    CookbookResponse create(String userEmail, CreateCookbookRequest request);

    List<CookbookResponse> getMyCookbooks(String userEmail);

    CookbookResponse getCookbook(UUID id, String userEmail);

    MessageResponse delete(UUID id, String userEmail);
}
