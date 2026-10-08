package com.cooked.backend.service;

import com.cooked.backend.dto.response.CreatorDetailResponse;

import java.util.UUID;

public interface AdminCreatorService {
    CreatorDetailResponse detail(UUID userId);

    /** App views of all creators' public recipes in the last [days] days. */
    long viewsSince(int days);
}
