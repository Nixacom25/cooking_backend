package com.cooked.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Declare an incident (title required) or update one (null fields unchanged). */
public record IncidentRequest(
        @NotBlank(groups = Create.class) @Size(max = 160) String title,
        @Size(max = 2000) String message,
        @Pattern(regexp = "MINOR|MAJOR|CRITICAL") String severity,
        @Pattern(regexp = "OPEN|MONITORING|RESOLVED") String status) {

    public interface Create extends jakarta.validation.groups.Default {
    }
}
