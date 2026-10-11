package com.cooked.backend.controller;

import com.cooked.backend.dto.request.EmailCampaignRequest;
import com.cooked.backend.dto.request.EmailCampaignScheduleRequest;
import com.cooked.backend.dto.response.EmailAudienceCount;
import com.cooked.backend.dto.response.EmailCampaignResponse;
import com.cooked.backend.service.EmailCampaignService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Growth → Email campaigns. HTTP only: the work is in {@link EmailCampaignService}. */
@RestController
@RequestMapping("/api/admin/email-campaigns")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin email campaigns", description = "Marketing emails through Brevo (unsubscribe + 2 per week cap)")
public class AdminEmailCampaignController {

    private final EmailCampaignService service;

    @GetMapping("/audiences")
    public ResponseEntity<List<EmailAudienceCount>> audiences() {
        return ResponseEntity.ok(service.audiences());
    }

    @GetMapping
    public ResponseEntity<Page<EmailCampaignResponse>> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(service.list(page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmailCampaignResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping
    public ResponseEntity<EmailCampaignResponse> create(@Valid @RequestBody EmailCampaignRequest body, Authentication auth) {
        return ResponseEntity.ok(service.create(body, auth.getName()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EmailCampaignResponse> update(@PathVariable UUID id, @Valid @RequestBody EmailCampaignRequest body) {
        return ResponseEntity.ok(service.update(id, body));
    }

    @GetMapping(value = "/{id}/preview", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> preview(@PathVariable UUID id) {
        return ResponseEntity.ok(service.preview(id));
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<EmailCampaignResponse> test(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(service.sendTest(id, auth.getName()));
    }

    @PostMapping("/{id}/schedule")
    public ResponseEntity<EmailCampaignResponse> schedule(@PathVariable UUID id, @RequestBody EmailCampaignScheduleRequest body, Authentication auth) {
        return ResponseEntity.ok(service.schedule(id, body, auth.getName()));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<EmailCampaignResponse> cancel(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(service.cancel(id, auth.getName()));
    }
}
