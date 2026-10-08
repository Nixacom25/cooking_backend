package com.cooked.backend.service;

import com.cooked.backend.dto.request.IngredientVisualCreateRequest;
import com.cooked.backend.dto.request.IngredientVisualFilter;
import com.cooked.backend.dto.request.IngredientVisualUpdateRequest;
import com.cooked.backend.dto.response.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Ingredient Visual Library: art, aliases and animation of every ingredient, and the manifests the app downloads. */
public interface IngredientCatalogService {

    IngredientLibraryResponse library(IngredientVisualFilter filter, String sort, int page, int size);

    IngredientVisualResponse get(UUID id);

    IngredientCatalogMetaResponse meta();

    /** Duplicate guard: which visual (other than excludeId) the name already resolves to. */
    IngredientNameCheckResponse checkName(String name, UUID excludeId);

    IngredientVisualResponse create(IngredientVisualCreateRequest request, String adminEmail);

    IngredientVisualResponse update(UUID id, IngredientVisualUpdateRequest request, String adminEmail);

    /** Replaces the art with an uploaded SVG (validated); uploaded art counts as reviewed. */
    IngredientVisualResponse replaceAsset(UUID id, String svg, String adminEmail);

    IngredientVisualResponse addAlias(UUID id, String alias, String adminEmail);

    IngredientReleaseResponse publish(String notes, String adminEmail);

    List<IngredientReleaseResponse> releases();

    /** Manifest of the catalog as it is now (what Publish would ship). */
    String draftManifest();

    /** Manifest of the latest published release. */
    Optional<String> publishedManifest();

    /** Adds the starter art (skips ids and names already in the catalog). */
    CatalogSeedResponse installStarterPack(String adminEmail);
}
