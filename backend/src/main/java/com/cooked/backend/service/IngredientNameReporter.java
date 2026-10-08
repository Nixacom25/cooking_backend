package com.cooked.backend.service;

import com.cooked.backend.entity.IngredientNameSource;

import java.util.Collection;

/** Fire-and-forget hook for app endpoints: never slows down or fails the user's request. */
public interface IngredientNameReporter {

    void report(Collection<String> names, IngredientNameSource source, String userEmail);
}
