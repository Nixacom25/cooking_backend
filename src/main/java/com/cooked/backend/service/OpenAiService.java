package com.cooked.backend.service;

import com.cooked.backend.dto.request.CreateRecipeRequest;
import org.springframework.web.multipart.MultipartFile;

public interface OpenAiService {
    CreateRecipeRequest extractRecipeFromLink(String url, String email);

    CreateRecipeRequest extractRecipeFromImage(MultipartFile file, String email);
}
