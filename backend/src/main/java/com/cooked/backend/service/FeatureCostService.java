package com.cooked.backend.service;

import com.cooked.backend.dto.response.FeatureCostResponse;

/** Cost per feature, estimated from AI spend and measured call volumes. */
public interface FeatureCostService {
    FeatureCostResponse features(int days);
}
