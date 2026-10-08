package com.cooked.backend.dto.request;

import com.cooked.backend.entity.IngredientNameSource;
import com.cooked.backend.entity.UnmatchedStatus;

/** Not-in-catalog queue filters; days = names seen in the last N days (null = any time). */
public record UnmatchedIngredientFilter(String q, IngredientNameSource source, UnmatchedStatus status, Integer days) {
}
