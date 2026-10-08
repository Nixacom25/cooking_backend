package com.cooked.backend.controller;

import com.cooked.backend.dto.request.IncidentRequest;
import com.cooked.backend.dto.response.IncidentResponse;
import com.cooked.backend.dto.response.SystemStatusResponse;
import com.cooked.backend.service.IncidentService;
import com.cooked.backend.service.SystemStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** System health and incidents. HTTP only. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin System", description = "Live health of the platform and incidents")
public class AdminSystemController {

    private final SystemStatusService status;
    private final IncidentService incidents;

    @Operation(summary = "Request metrics (15 min), database latency, scheduled-job failures, per-service status, open incidents")
    @GetMapping("/system/status")
    public ResponseEntity<SystemStatusResponse> status() {
        return ResponseEntity.ok(status.status());
    }

    @Operation(summary = "Last 50 incidents, newest first")
    @GetMapping("/incidents")
    public ResponseEntity<List<IncidentResponse>> incidents() {
        return ResponseEntity.ok(incidents.recent());
    }

    @Operation(summary = "Declare an incident (severity MINOR|MAJOR|CRITICAL)")
    @PostMapping("/incidents")
    public ResponseEntity<IncidentResponse> declare(@Validated(IncidentRequest.Create.class) @RequestBody IncidentRequest body, Authentication auth) {
        return ResponseEntity.ok(incidents.declare(body, auth.getName()));
    }

    @Operation(summary = "Update an incident (status OPEN|MONITORING|RESOLVED)")
    @PatchMapping("/incidents/{id}")
    public ResponseEntity<IncidentResponse> update(@PathVariable UUID id, @Validated @RequestBody IncidentRequest body) {
        return ResponseEntity.ok(incidents.update(id, body));
    }
}
