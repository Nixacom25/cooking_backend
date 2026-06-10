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
    private final com.cooked.backend.service.AnalyticsService analyticsService;

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

    @GetMapping("/transactions")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.springframework.data.domain.Page<Map<String, Object>>> getTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
                
        String[] sortParams = sort.split(",");
        org.springframework.data.domain.Sort.Direction direction = sortParams.length > 1
                && sortParams[1].equalsIgnoreCase("asc") ? org.springframework.data.domain.Sort.Direction.ASC
                        : org.springframework.data.domain.Sort.Direction.DESC;
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by(direction, sortParams[0]));

        return ResponseEntity.ok(analyticsService.getTransactions(pageable));
    }

    @GetMapping("/subscriptions")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<org.springframework.data.domain.Page<Map<String, Object>>> getActiveSubscriptions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
                
        String[] sortParams = sort.split(",");
        org.springframework.data.domain.Sort.Direction direction = sortParams.length > 1
                && sortParams[1].equalsIgnoreCase("asc") ? org.springframework.data.domain.Sort.Direction.ASC
                        : org.springframework.data.domain.Sort.Direction.DESC;
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by(direction, sortParams[0]));

        return ResponseEntity.ok(analyticsService.getActiveSubscriptions(pageable));
    }
}
