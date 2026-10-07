package com.cooked.backend.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Admin triage of a support ticket. A null field is left unchanged; an empty assignee unassigns. */
public record TicketTriageRequest(
        @Size(max = 160) String assignee,
        @Pattern(regexp = "NORMAL|HIGH|URGENT", message = "priority must be NORMAL, HIGH or URGENT") String priority) {
}
