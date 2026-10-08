package com.cooked.backend.service;

import com.cooked.backend.dto.request.UnmatchedIngredientFilter;
import com.cooked.backend.dto.response.CatalogSeedResponse;
import com.cooked.backend.dto.response.UnmatchedIngredientResponse;
import com.cooked.backend.dto.response.UnmatchedQueueResponse;
import com.cooked.backend.entity.IngredientNameSource;

import java.util.Collection;
import java.util.UUID;

/** Not-in-catalog queue: ingredient names seen in the app that no visual resolves. */
public interface UnmatchedIngredientService {

    /** Counts the names the catalog does not resolve (synchronous; see IngredientNameReporter). */
    int record(Collection<String> names, IngredientNameSource source, String userEmail);

    UnmatchedQueueResponse queue(UnmatchedIngredientFilter filter, String sort, int page, int size);

    UnmatchedIngredientResponse addAsAlias(UUID id, UUID visualId, String adminEmail);

    UnmatchedIngredientResponse ignore(UUID id, String adminEmail);

    UnmatchedIngredientResponse reopen(UUID id);

    /** Queues the most used recipe ingredient names the catalog does not resolve yet. */
    CatalogSeedResponse backfillFromRecipes(int limit);
}
