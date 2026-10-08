package com.cooked.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Page view of the public website.
 *
 * @param visitor random id kept in the visitor's browser (no personal data)
 * @param path    page path, e.g. /blog/my-article
 * @param referrer referring host, e.g. google.com (optional)
 */
public record SiteVisitRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{8,64}") String visitor,
        @NotBlank @Size(max = 200) @Pattern(regexp = "/.*") String path,
        @Size(max = 120) String referrer) {
}
