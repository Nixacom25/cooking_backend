package com.cooked.backend.controller;

import com.cooked.backend.dto.request.CreateGroceryItemRequest;
import com.cooked.backend.dto.response.GroceryItemResponse;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.service.GroceryItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/grocery-items")
@RequiredArgsConstructor
@Tag(name = "Grocery", description = "Endpoints for managing the smart shopping list")
@SecurityRequirement(name = "bearerAuth")
public class GroceryItemController {

    private final GroceryItemService groceryItemService;

    @Operation(summary = "Manually add an item to the grocery list")
    @PostMapping
    public ResponseEntity<GroceryItemResponse> create(Authentication auth,
            @Valid @RequestBody CreateGroceryItemRequest request) {
        return ResponseEntity.ok(groceryItemService.create(auth.getName(), request));
    }

    @Operation(summary = "Get my entire grocery list")
    @GetMapping
    public ResponseEntity<List<GroceryItemResponse>> getMyGroceryItems(Authentication auth) {
        return ResponseEntity.ok(groceryItemService.getMyGroceryItems(auth.getName()));
    }

    @Operation(summary = "Get grocery list items for a specific date filter")
    @GetMapping("/date/{date}")
    public ResponseEntity<List<GroceryItemResponse>> getMyGroceryItemsByDate(
            Authentication auth,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(groceryItemService.getMyGroceryItemsByDate(auth.getName(), date));
    }

    @Operation(summary = "Toggle the bought status (checkbox) of an item")
    @PutMapping("/{id}/toggle")
    public ResponseEntity<GroceryItemResponse> toggleBought(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(groceryItemService.toggleBought(id, auth.getName()));
    }

    @Operation(summary = "Delete an item from the grocery list")
    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> delete(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(groceryItemService.delete(id, auth.getName()));
    }
}
