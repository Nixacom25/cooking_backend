package com.cooked.backend.service;

import com.cooked.backend.dto.request.OnboardingStepRequest;
import com.cooked.backend.dto.response.OnboardingFunnelResponse;

import java.time.LocalDate;

/** Onboarding screens seen in the app (anonymous) and the admin funnel built from them. */
public interface OnboardingFunnelService {

    /** Records the first view of a screen by an install; repeats and floods are ignored. */
    void record(OnboardingStepRequest request, String platform, String appVersion);

    /** Funnel of the installs that saw the first screen between from and to (inclusive); platform ios / android / null. */
    OnboardingFunnelResponse funnel(LocalDate from, LocalDate to, String platform);
}
