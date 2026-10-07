package com.cooked.backend.service;

import com.cooked.backend.dto.response.EmailSummaryResponse;
import com.cooked.backend.dto.response.IntegrationDetailResponse;
import com.cooked.backend.dto.response.IntegrationStatusResponse;

import java.util.List;

/** Status and activity of external services, and the emails sent through them. */
public interface AdminIntegrationService {

    int MAX_PAGE_SIZE = 100;

    List<IntegrationStatusResponse> integrations();

    IntegrationDetailResponse integration(String key, int page, int size);

    EmailSummaryResponse emails(int days);
}
