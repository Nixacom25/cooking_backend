package com.cooked.backend.service;

import com.cooked.backend.dto.request.ArticleRequest;
import com.cooked.backend.dto.response.ArticleResponse;
import com.cooked.backend.dto.response.PagedResponse;
import com.cooked.backend.entity.ArticleStatus;

import java.util.UUID;

/** Website articles: backoffice editing + public reading (published only). */
public interface ArticleService {

    PagedResponse<ArticleResponse> list(ArticleStatus status, int page, int size);

    ArticleResponse get(UUID id);

    ArticleResponse create(ArticleRequest request, String adminEmail);

    ArticleResponse update(UUID id, ArticleRequest request);

    ArticleResponse moveTo(UUID id, ArticleStatus status);

    void delete(UUID id);

    PagedResponse<ArticleResponse> published(int page, int size);

    ArticleResponse publishedBySlug(String slug);

    /** Publishes SCHEDULED articles whose time has come; returns how many. */
    int publishDue();
}
