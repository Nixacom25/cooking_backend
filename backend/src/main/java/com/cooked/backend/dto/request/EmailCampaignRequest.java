package com.cooked.backend.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A campaign draft (every field optional until it is scheduled). */
public record EmailCampaignRequest(
        @Pattern(regexp = "TRIAL|TRIAL_ENDING|FREE|PAYING|INACTIVE_30") String audience,
        @Size(max = 150) String subject,
        @Size(max = 200) String preheader,
        @Pattern(regexp = "TRIAL_ENDING|BLANK") String template,
        @Size(max = 120) String title,
        @Size(max = 5000) String body,
        @Size(max = 40) String buttonLabel,
        @Size(max = 500) @Pattern(regexp = "^$|https://.*", message = "The button link must start with https://") String buttonUrl,
        @Size(max = 500) @Pattern(regexp = "^$|https://.*", message = "The image link must start with https://") String heroImageUrl) {
}
