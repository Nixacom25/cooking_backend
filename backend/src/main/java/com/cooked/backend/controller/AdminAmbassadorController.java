package com.cooked.backend.controller;

import com.cooked.backend.dto.request.AmbassadorRequest;
import com.cooked.backend.dto.response.*;
import com.cooked.backend.entity.CreatorApplicationStatus;
import com.cooked.backend.service.AmbassadorService;
import com.cooked.backend.service.CreatorApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/** Ambassadors and creator applications. HTTP only: the work is in the services. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Ambassadors & creators", description = "Referral codes, attribution, commissions, payouts and applications")
public class AdminAmbassadorController {

    private final AmbassadorService ambassadors;
    private final CreatorApplicationService applications;

    @Operation(summary = "Ambassadors with clicks, code sign-ups, trials, paid, revenue and commission (days: 1-90)")
    @GetMapping("/ambassadors")
    public ResponseEntity<AmbassadorsResponse> list(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(ambassadors.list(days));
    }

    @GetMapping("/ambassadors/{id}")
    public ResponseEntity<AmbassadorDetailResponse> detail(@PathVariable UUID id, @RequestParam(defaultValue = "90") int days) {
        return ResponseEntity.ok(ambassadors.detail(id, days));
    }

    @PostMapping("/ambassadors")
    public ResponseEntity<AmbassadorResponse> create(@Valid @RequestBody AmbassadorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ambassadors.create(request));
    }

    @PutMapping("/ambassadors/{id}")
    public ResponseEntity<AmbassadorResponse> update(@PathVariable UUID id, @Valid @RequestBody AmbassadorRequest request) {
        return ResponseEntity.ok(ambassadors.update(id, request));
    }

    @Operation(summary = "Mark a finished month as paid (amounts frozen)")
    @PostMapping("/ambassadors/{id}/payouts/{month}/paid")
    public ResponseEntity<AmbassadorDetailResponse> markPaid(@PathVariable UUID id, @PathVariable String month, Authentication auth) {
        return ResponseEntity.ok(ambassadors.markPaid(id, month, auth.getName()));
    }

    @GetMapping("/creator-applications")
    public ResponseEntity<PagedResponse<CreatorApplicationResponse>> applications(@RequestParam(required = false) CreatorApplicationStatus status,
                                                                                  @RequestParam(defaultValue = "0") int page,
                                                                                  @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(applications.list(status, page, size));
    }

    @GetMapping("/creator-applications/counts")
    public ResponseEntity<Map<String, Long>> counts() {
        return ResponseEntity.ok(applications.counts());
    }

    @Operation(summary = "Approve / reject / request info (emails the applicant; approving an ambassador creates their code)")
    @PostMapping("/creator-applications/{id}/decision")
    public ResponseEntity<CreatorApplicationResponse> decide(@PathVariable UUID id, @RequestBody Map<String, String> body, Authentication auth) {
        CreatorApplicationStatus status;
        try {
            status = CreatorApplicationStatus.valueOf(String.valueOf(body.get("status")));
        } catch (IllegalArgumentException e) {
            throw new com.cooked.backend.exception.BadRequestException("Unknown decision");
        }
        return ResponseEntity.ok(applications.decide(id, status, body.get("message"), auth.getName()));
    }
}
