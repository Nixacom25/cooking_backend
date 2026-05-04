package com.cooked.backend.controller;

import com.cooked.backend.entity.AnalyticsEvent;
import com.cooked.backend.repository.AnalyticsEventRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Paywall Funnel Tracking")
@SecurityRequirement(name = "bearerAuth")
public class AnalyticsController {

    private final AnalyticsEventRepository analyticsEventRepository;

    @Operation(summary = "Track Paywall Event")
    @PostMapping("/track")
    public ResponseEntity<?> trackEvent(Authentication auth, @RequestBody Map<String, String> payload) {
        AnalyticsEvent event = AnalyticsEvent.builder()
                .eventName(payload.get("eventName"))
                .variantKey(payload.get("variantKey"))
                .userEmail(auth != null ? auth.getName() : "anonymous")
                .metadata(payload.get("metadata"))
                .build();
        
        analyticsEventRepository.save(event);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
