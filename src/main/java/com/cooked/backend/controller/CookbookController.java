package com.cooked.backend.controller;

import com.cooked.backend.dto.request.CreateCookbookRequest;
import com.cooked.backend.dto.response.CookbookResponse;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.service.CookbookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/cookbooks")
@RequiredArgsConstructor
@Tag(name = "Cookbook", description = "Endpoints for managing personal Cookbooks")
@SecurityRequirement(name = "bearerAuth")
public class CookbookController {

    private final CookbookService cookbookService;

    @Operation(summary = "Create a new Cookbook")
    @PostMapping
    public ResponseEntity<CookbookResponse> create(Authentication auth,
            @Valid @RequestBody CreateCookbookRequest request) {
        return ResponseEntity.ok(cookbookService.create(auth.getName(), request));
    }

    @Operation(summary = "Get all my Cookbooks")
    @GetMapping
    public ResponseEntity<List<CookbookResponse>> getMyCookbooks(Authentication auth) {
        return ResponseEntity.ok(cookbookService.getMyCookbooks(auth.getName()));
    }

    @Operation(summary = "Get a specific Cookbook")
    @GetMapping("/{id}")
    public ResponseEntity<CookbookResponse> getCookbook(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(cookbookService.getCookbook(id, auth.getName()));
    }

    @Operation(summary = "Delete a Cookbook")
    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> delete(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(cookbookService.delete(id, auth.getName()));
    }
}
