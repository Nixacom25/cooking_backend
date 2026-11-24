package com.cooked.backend.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * An onboarding screen shown in the app (anonymous: the user has no account yet).
 *
 * @param installId random id kept on the device (no personal data)
 * @param step      page index, 0 = first screen
 * @param screen    screen name, e.g. "Goals"
 */
public record OnboardingStepRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{8,64}") String installId,
        @Min(0) @Max(99) int step,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9 ()&'.,-]{1,40}") String screen) {
}
