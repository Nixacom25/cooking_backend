package com.cooked.backend.controller;

import com.cooked.backend.dto.request.*;
import com.cooked.backend.dto.response.*;
import com.cooked.backend.entity.IngredientDelivery;
import com.cooked.backend.entity.IngredientNameSource;
import com.cooked.backend.entity.IngredientVisualStatus;
import com.cooked.backend.entity.UnmatchedStatus;
import com.cooked.backend.service.IngredientCatalogService;
import com.cooked.backend.service.UnmatchedIngredientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Ingredient Visual Library and the Not-in-catalog queue. HTTP only. */
@RestController
@RequestMapping("/api/admin/ingredient-catalog")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Ingredient Catalog", description = "Ingredient art, aliases, animations, releases and unmatched names")
public class AdminIngredientCatalogController {

    private final IngredientCatalogService catalog;
    private final UnmatchedIngredientService queue;

    @Operation(summary = "Library page: rows, status tab counts, KPIs, filter options and publish state")
    @GetMapping
    public ResponseEntity<IngredientLibraryResponse> library(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) IngredientVisualStatus status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String collection,
            @RequestParam(required = false) String animation,
            @RequestParam(required = false) IngredientDelivery delivery,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(catalog.library(new IngredientVisualFilter(q, status, category, collection, animation, delivery),
                sort, page, size));
    }

    @Operation(summary = "Presets, categories, reusable art and upload rules for the drawers")
    @GetMapping("/meta")
    public ResponseEntity<IngredientCatalogMetaResponse> meta() {
        return ResponseEntity.ok(catalog.meta());
    }

    @Operation(summary = "Duplicate guard: which ingredient a name already resolves to")
    @GetMapping("/check-name")
    public ResponseEntity<IngredientNameCheckResponse> checkName(@RequestParam String name,
                                                                 @RequestParam(required = false) UUID excludeId) {
        return ResponseEntity.ok(catalog.checkName(name, excludeId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<IngredientVisualResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(catalog.get(id));
    }

    @Operation(summary = "Add a canonical ingredient (starts as Needs Review)")
    @PostMapping
    public ResponseEntity<IngredientVisualResponse> create(@Valid @RequestBody IngredientVisualCreateRequest body, Authentication auth) {
        return ResponseEntity.ok(catalog.create(body, auth.getName()));
    }

    @Operation(summary = "Edit name, category, aliases, animation, delivery, review state or enable/disable")
    @PatchMapping("/{id}")
    public ResponseEntity<IngredientVisualResponse> update(@PathVariable UUID id, @Valid @RequestBody IngredientVisualUpdateRequest body,
                                                           Authentication auth) {
        return ResponseEntity.ok(catalog.update(id, body, auth.getName()));
    }

    @Operation(summary = "Replace the art with an SVG (64×64, ≤ 12 KB, no scripts/images/filters)")
    @PutMapping("/{id}/asset")
    public ResponseEntity<IngredientVisualResponse> replaceAsset(@PathVariable UUID id, @Valid @RequestBody SvgAssetRequest body,
                                                                 Authentication auth) {
        return ResponseEntity.ok(catalog.replaceAsset(id, body.svg(), auth.getName()));
    }

    @Operation(summary = "Publish the catalog as the next version")
    @PostMapping("/publish")
    public ResponseEntity<IngredientReleaseResponse> publish(@Valid @RequestBody(required = false) PublishCatalogRequest body,
                                                             Authentication auth) {
        return ResponseEntity.ok(catalog.publish(body == null ? null : body.notes(), auth.getName()));
    }

    @GetMapping("/releases")
    public ResponseEntity<List<IngredientReleaseResponse>> releases() {
        return ResponseEntity.ok(catalog.releases());
    }

    @Operation(summary = "Download the manifest of the catalog as it is now")
    @GetMapping(value = "/manifest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> manifest() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ingredient-manifest.json\"")
                .body(catalog.draftManifest());
    }

    @Operation(summary = "Add every database ingredient the catalog does not resolve yet, as Missing Asset entries")
    @PostMapping("/import-ingredients")
    public ResponseEntity<CatalogSeedResponse> importIngredients(Authentication auth) {
        try {
            return ResponseEntity.ok(catalog.importDatabaseIngredients(auth.getName()));
        } catch (RuntimeException e) {
            // admin-only bulk job: say what went wrong instead of a bare "Server error"
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Import failed: " + root.getClass().getSimpleName() + ": " + String.valueOf(root.getMessage()).lines().findFirst().orElse(""), e);
        }
    }

    @Operation(summary = "Install the starter art pack (skips what already exists)")
    @PostMapping("/starter-pack")
    public ResponseEntity<CatalogSeedResponse> starterPack(Authentication auth) {
        return ResponseEntity.ok(catalog.installStarterPack(auth.getName()));
    }

    // ------------------------------------------------------------ Not in catalog

    @Operation(summary = "Names seen in the app that no ingredient resolves")
    @GetMapping("/unmatched")
    public ResponseEntity<UnmatchedQueueResponse> unmatched(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) IngredientNameSource source,
            @RequestParam(defaultValue = "OPEN") UnmatchedStatus status,
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(queue.queue(new UnmatchedIngredientFilter(q, source, status, days), sort, page, size));
    }

    @Operation(summary = "Resolve a name by adding it as an alias of an ingredient")
    @PostMapping("/unmatched/{id}/alias")
    public ResponseEntity<UnmatchedIngredientResponse> addAsAlias(@PathVariable UUID id, @Valid @RequestBody UnmatchedAliasRequest body,
                                                                  Authentication auth) {
        return ResponseEntity.ok(queue.addAsAlias(id, body.visualId(), auth.getName()));
    }

    @PostMapping("/unmatched/{id}/ignore")
    public ResponseEntity<UnmatchedIngredientResponse> ignore(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(queue.ignore(id, auth.getName()));
    }

    @PostMapping("/unmatched/{id}/reopen")
    public ResponseEntity<UnmatchedIngredientResponse> reopen(@PathVariable UUID id) {
        return ResponseEntity.ok(queue.reopen(id));
    }

    @Operation(summary = "Queue the most used recipe ingredient names the catalog does not resolve")
    @PostMapping("/unmatched/backfill")
    public ResponseEntity<CatalogSeedResponse> backfill(@RequestParam(defaultValue = "500") int limit) {
        return ResponseEntity.ok(queue.backfillFromRecipes(limit));
    }
}
