package com.cooked.backend.controller;

import com.cooked.backend.dto.request.CriticalErrorRequest;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.entity.CriticalError;
import com.cooked.backend.repository.CriticalErrorRepository;
import com.cooked.backend.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/errors")
@RequiredArgsConstructor
@Tag(name = "Error Monitoring", description = "Critical error reporting and monitoring")
public class ErrorMonitoringController {

    private final CriticalErrorRepository criticalErrorRepository;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    @Value("${support.notification.email:contact@cookedapp.com}")
    private String supportTeamEmail;

    @Operation(summary = "Submit a critical error from mobile app")
    @PostMapping("/critical")
    public ResponseEntity<MessageResponse> submitCriticalError(@RequestBody CriticalErrorRequest request) {
        String contextJson = "";
        if (request.getContext() != null) {
            try {
                contextJson = objectMapper.writeValueAsString(request.getContext());
            } catch (Exception e) {
                contextJson = request.getContext().toString();
            }
        }

        CriticalError error = CriticalError.builder()
                .errorType(request.getErrorType())
                .errorMessage(request.getErrorMessage())
                .stackTrace(request.getStackTrace())
                .userId(request.getUserId())
                .userEmail(request.getUserEmail())
                .platform(request.getPlatform())
                .osVersion(request.getOsVersion())
                .appVersion(request.getAppVersion())
                .context(contextJson)
                .build();

        CriticalError saved = criticalErrorRepository.save(error);
        String errorId = shortErrorId(saved.getId());

        // Send immediate email alert for critical errors
        emailService.sendCriticalErrorAlert(supportTeamEmail, errorId, saved.getErrorType(),
                saved.getErrorMessage(), saved.getUserId(), saved.getUserEmail(), saved.getPlatform(),
                saved.getOsVersion(), saved.getAppVersion(), saved.getContext());

        return ResponseEntity.ok(new MessageResponse("Critical error recorded successfully"));
    }

    @Operation(summary = "List critical errors (admin only)")
    @GetMapping("/critical")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<CriticalError>> listCriticalErrors(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Callers that only want the total count (e.g. the admin bell's
        // unread-critical-errors badge) legitimately ask for size=0, but
        // Spring's PageRequest rejects page < 0 or size < 1 - clamp instead
        // of crashing; totalElements is accurate regardless of page size.
        Pageable pageable = PageRequest.of(com.cooked.backend.util.PaginationUtils.clampPage(page),
                com.cooked.backend.util.PaginationUtils.clampSize(size), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CriticalError> errors = (status == null || status.isBlank())
                ? criticalErrorRepository.findAllByOrderByCreatedAtDesc(pageable)
                : criticalErrorRepository.findAllByStatusOrderByCreatedAtDesc(status.toUpperCase(), pageable);
        return ResponseEntity.ok(errors);
    }

    @Operation(summary = "Update error status (admin only)")
    @PutMapping("/critical/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CriticalError> updateErrorStatus(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        CriticalError error = criticalErrorRepository.findById(id)
                .orElseThrow(() -> new com.cooked.backend.exception.ResourceNotFoundException("Error not found"));
        String status = body.get("status");
        if (status != null && !status.isBlank()) {
            error.setStatus(status.toUpperCase());
            criticalErrorRepository.save(error);
        }
        return ResponseEntity.ok(error);
    }

    private String shortErrorId(UUID id) {
        return id.toString().substring(0, 8).toUpperCase();
    }
}