package com.cooked.backend.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * Onboarding funnel of the installs that saw the first screen in [from, to].
 *
 * @param started                installs that saw the first screen
 * @param finished               installs that saw the last screen
 * @param medianSecondsToFinish  median time from the first to the last screen (null: nobody finished)
 * @param steps                  every screen in app order
 */
public record OnboardingFunnelResponse(
        LocalDate from,
        LocalDate to,
        String platform,
        long started,
        long finished,
        Long medianSecondsToFinish,
        int lastStep,
        List<Step> steps) {

    /**
     * @param reached users who saw this screen
     * @param leftHere users whose furthest screen is this one
     */
    public record Step(int step, String screen, long reached, long leftHere) {
    }
}
