package com.cooked.backend.controller;

import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.service.ExploreDataSeederService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/seed")
@RequiredArgsConstructor
@Tag(name = "Admin Seeder", description = "Endpoints for seeding data manually")
public class ExploreSeederController {

    private final ExploreDataSeederService exploreDataSeederService;

    @Operation(summary = "Seed Explore Recipes")
    @PostMapping("/explore")
    public ResponseEntity<?> seedExplore() {
        try {
            exploreDataSeederService.seedExploreData();
            return ResponseEntity.ok(new MessageResponse("Seeding process triggered successfully."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new MessageResponse("Error: " + e.getMessage()));
        }
    }
}
