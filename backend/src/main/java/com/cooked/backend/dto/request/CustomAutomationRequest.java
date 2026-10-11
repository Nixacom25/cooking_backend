package com.cooked.backend.dto.request;

import jakarta.validation.constraints.*;

/** A custom automation: When (trigger + value) → If (audience, app opens) → Then (actions). */
public record CustomAutomationRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 40) String templateKey,
        @NotBlank @Pattern(regexp = "TRIAL_ENDS_IN|SIGNED_UP_DAYS_AGO|INACTIVE_DAYS|PAYMENT_FAILED|IMPORT_FAILED|WEEKLY_MONDAY|CREATOR_APPLIED|ERRORS_SPIKE") String trigger,
        Integer triggerValue,
        @Pattern(regexp = "ANY|TRIAL|FREE|PAYING") String audience,
        @Pattern(regexp = "ANY|WITHIN_7_DAYS|NOT_IN_7_DAYS") String opened,
        boolean pushEnabled, @Size(max = 80) String pushTitle, @Size(max = 200) String pushBody,
        boolean emailEnabled, @Size(max = 150) String emailSubject, @Size(max = 120) String emailTitle, @Size(max = 5000) String emailBody,
        @Size(max = 40) String emailButton, @Size(max = 500) @Pattern(regexp = "^$|https://.*", message = "The button link must start with https://") String emailButtonUrl,
        boolean slackEnabled, @Size(max = 300) String slackText,
        boolean teamEmailEnabled) {
}
