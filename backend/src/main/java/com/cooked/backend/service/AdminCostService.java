package com.cooked.backend.service;

import com.cooked.backend.dto.request.CreateCostRequest;
import com.cooked.backend.dto.request.UpdateProviderBudgetRequest;
import com.cooked.backend.dto.response.CostEntryResponse;
import com.cooked.backend.dto.response.CostOverviewResponse;
import com.cooked.backend.dto.response.ProviderCostDetailResponse;

import java.util.List;
import java.util.UUID;

/** Cost Center: what Cooked costs to run (manual entries + provider billing APIs), in USD. */
public interface AdminCostService {

    int MAX_DAYS = 90;

    CostOverviewResponse overview(int days);

    ProviderCostDetailResponse provider(String provider, int days);

    List<CostEntryResponse> entries();

    CostEntryResponse addEntry(CreateCostRequest request, String adminEmail);

    void deleteEntry(UUID id);

    CostOverviewResponse.ProviderRow updateBudget(String provider, UpdateProviderBudgetRequest request);

    /** Pulls recent spend from every configured billing API; returns the number of rows stored. */
    int syncProviders();
}
