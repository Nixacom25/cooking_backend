package com.cooked.backend.controller;

import com.cooked.backend.dto.request.CustomAutomationRequest;
import com.cooked.backend.dto.response.AutomationTemplateResponse;
import com.cooked.backend.dto.response.CustomAutomationResponse;
import com.cooked.backend.service.CustomAutomationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Growth → Automations → New automation / Templates. HTTP only: the work is in {@link CustomAutomationService}. */
@RestController
@RequestMapping("/api/admin/automations/custom")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin custom automations", description = "No-code flows: When → If → Then, dry run before activation")
public class AdminCustomAutomationController {

    private final CustomAutomationService service;

    @GetMapping("/templates")
    public ResponseEntity<List<AutomationTemplateResponse>> templates() {
        return ResponseEntity.ok(service.templates());
    }

    @GetMapping
    public ResponseEntity<List<CustomAutomationResponse>> list() {
        return ResponseEntity.ok(service.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomAutomationResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping
    public ResponseEntity<CustomAutomationResponse> create(@Valid @RequestBody CustomAutomationRequest body, Authentication auth) {
        return ResponseEntity.ok(service.create(body, auth.getName()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CustomAutomationResponse> update(@PathVariable UUID id, @Valid @RequestBody CustomAutomationRequest body, Authentication auth) {
        return ResponseEntity.ok(service.update(id, body, auth.getName()));
    }

    @PostMapping("/{id}/dry-run")
    public ResponseEntity<CustomAutomationResponse> dryRun(@PathVariable UUID id) {
        return ResponseEntity.ok(service.dryRun(id));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<CustomAutomationResponse> activate(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(service.activate(id, auth.getName()));
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<CustomAutomationResponse> pause(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(service.pause(id, auth.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication auth) {
        service.delete(id, auth.getName());
        return ResponseEntity.noContent().build();
    }
}
