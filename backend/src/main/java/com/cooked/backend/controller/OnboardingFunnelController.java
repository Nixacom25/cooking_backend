package com.cooked.backend.controller;

import com.cooked.backend.dto.request.OnboardingStepRequest;
import com.cooked.backend.dto.response.OnboardingFunnelResponse;
import com.cooked.backend.service.OnboardingFunnelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** Onboarding screens seen in the app (anonymous) and the admin funnel. HTTP only. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Onboarding funnel")
public class OnboardingFunnelController {

    private static final int MAX_DAYS = 366;

    private final OnboardingFunnelService funnel;

    @Operation(summary = "An onboarding screen shown in the app (anonymous, one per install and screen, rate limited)")
    @PostMapping("/public/onboarding/steps")
    public ResponseEntity<Void> step(@Valid @RequestBody OnboardingStepRequest body,
                                     @RequestHeader(value = "X-Client-Platform", required = false) String platform,
                                     @RequestHeader(value = "X-App-Version", required = false) String appVersion) {
        funnel.record(body, platform, appVersion);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Onboarding funnel of the installs that started between from and to (default: last 30 days)")
    @GetMapping("/api/admin/analytics/onboarding-funnel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OnboardingFunnelResponse> funnel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String platform) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end)) start = end;
        if (start.isBefore(end.minusDays(MAX_DAYS))) start = end.minusDays(MAX_DAYS);
        return ResponseEntity.ok(funnel.funnel(start, end, platform));
    }
}
