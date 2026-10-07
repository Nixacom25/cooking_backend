package com.cooked.backend.service;

import com.cooked.backend.dto.response.CreatorDetailResponse;

import java.util.UUID;

public interface AdminCreatorService {
    CreatorDetailResponse detail(UUID userId);
}
