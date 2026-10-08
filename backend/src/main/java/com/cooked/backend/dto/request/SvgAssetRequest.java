package com.cooked.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Replacement art, as the SVG file's text. */
public record SvgAssetRequest(@NotBlank @Size(max = 64000) String svg) {
}
