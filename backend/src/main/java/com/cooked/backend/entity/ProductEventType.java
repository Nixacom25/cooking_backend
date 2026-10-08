package com.cooked.backend.entity;

/** Product actions measured server-side for the admin analytics screens. */
public enum ProductEventType {
    SCAN,
    IMPORT,
    WEB_SEARCH,
    // Sent by the app (POST /events)
    /** App in foreground; durationMs = session length. */
    APP_SESSION,
    /** Recipe screen opened; detail = recipe id. */
    RECIPE_VIEW,
    /** Web search result opened; detail = query. */
    SEARCH_OPEN,
    /** Web search result saved as a recipe; detail = query. */
    SEARCH_SAVE,
    /** Cooking mode started; detail = recipe id. */
    COOKING_MODE,
    /** Message sent to the in-app assistant. */
    ASSISTANT;

    /** Types the app may send itself (server-measured ones are refused). */
    public boolean fromApp() {
        return ordinal() > WEB_SEARCH.ordinal();
    }
}
