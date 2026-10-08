package com.cooked.backend.dto.response;

import java.util.UUID;

/** Result of an admin retry of a failed import (the recipe goes to the user's suggestions when it works). */
public record ImportRetryResponse(boolean success, String message, UUID recipeId) {
}
