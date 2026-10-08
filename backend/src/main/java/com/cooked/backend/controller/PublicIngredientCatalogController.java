package com.cooked.backend.controller;

import com.cooked.backend.service.IngredientCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/** The published ingredient catalog the app downloads (art, aliases, animations). */
@RestController
@RequiredArgsConstructor
@Tag(name = "Public Ingredient Catalog")
public class PublicIngredientCatalogController {

    private final IngredientCatalogService catalog;

    @Operation(summary = "Latest published ingredient manifest (404 before the first publish)")
    @GetMapping(value = "/public/ingredient-catalog/manifest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> manifest() {
        return catalog.publishedManifest()
                .map(m -> ResponseEntity.ok().cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePublic()).body(m))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
