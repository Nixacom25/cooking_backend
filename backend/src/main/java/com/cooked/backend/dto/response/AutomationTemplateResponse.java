package com.cooked.backend.dto.response;

import com.cooked.backend.dto.request.CustomAutomationRequest;

import java.util.List;

/** A ready-made flow; unavailable ones say why. */
public record AutomationTemplateResponse(String key, String name, String category, String description, String trigger,
                                         List<String> actions, boolean popular, boolean available, String reason,
                                         CustomAutomationRequest preset) {
}
