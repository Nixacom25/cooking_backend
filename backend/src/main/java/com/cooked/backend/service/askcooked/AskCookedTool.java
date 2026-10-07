package com.cooked.backend.service.askcooked;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * A read-only data source Ask Cooked may call (OpenAI function calling).
 * Add a bean to give the assistant a new capability; tools must never write
 * and must not return customers' personal data.
 */
public interface AskCookedTool {

    Map<String, Object> DAYS_PARAMETERS = Map.of(
            "type", "object",
            "properties", Map.of("days", Map.of("type", "integer", "minimum", 1, "maximum", 90,
                    "description", "Period length in days ending today (default 30)")),
            "required", java.util.List.of());

    Map<String, Object> NO_PARAMETERS = Map.of("type", "object", "properties", Map.of());

    /** Function name exposed to the model (snake_case). */
    String name();

    /** What the data covers, for the model. */
    String description();

    /** Label shown under the answer ("Product analytics"). */
    String sourceLabel();

    Map<String, Object> parameters();

    Object run(JsonNode arguments);

    static int days(JsonNode args) {
        int d = args == null ? 30 : args.path("days").asInt(30);
        return Math.max(1, Math.min(d, 90));
    }
}
