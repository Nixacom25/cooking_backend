package com.cooked.backend.service;

import com.cooked.backend.entity.Recipe;
import com.cooked.backend.entity.User;

/**
 * Gives a user their own private copy of someone else's recipe (saved from
 * Explore, or received through a shared cookbook).
 */
public interface RecipeCopyService {

    /**
     * Returns the user's copy of {@code original}: the one they already have
     * with the same name (restored if it was soft-deleted), or a new private
     * clone. A recipe the user already owns is returned as is.
     */
    Recipe copyForUser(Recipe original, User user);
}
