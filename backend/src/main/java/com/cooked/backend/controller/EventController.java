package com.cooked.backend.controller;

import com.cooked.backend.dto.request.AppEventsRequest;
import com.cooked.backend.dto.request.SiteVisitRequest;
import com.cooked.backend.service.AppEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Usage events from the app and page views from the website. HTTP only. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Events", description = "Product usage events (app) and website page views")
public class EventController {

    private final AppEventService events;

    @Operation(summary = "App usage events: APP_SESSION, RECIPE_VIEW, SEARCH_OPEN, SEARCH_SAVE, COOKING_MODE, ASSISTANT (max 50 per call)")
    @PostMapping("/events")
    public ResponseEntity<Map<String, Integer>> record(@Valid @RequestBody AppEventsRequest body, Authentication auth) {
        return ResponseEntity.ok(Map.of("recorded", events.record(body, auth.getName())));
    }

    @Operation(summary = "Website page view (anonymous, rate limited)")
    @PostMapping("/public/visits")
    public ResponseEntity<Void> visit(@Valid @RequestBody SiteVisitRequest body) {
        events.visit(body);
        return ResponseEntity.accepted().build();
    }
}
