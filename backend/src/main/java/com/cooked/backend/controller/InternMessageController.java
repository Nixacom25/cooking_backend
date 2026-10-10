package com.cooked.backend.controller;

import com.cooked.backend.dto.request.TextRequest;
import com.cooked.backend.dto.response.InternInboxResponse;
import com.cooked.backend.service.InternMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/** "Contact your manager" for data interns. HTTP only: the work is in {@link InternMessageService}. */
@RestController
@RequestMapping("/api/intern/messages")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
@Tag(name = "Intern messages", description = "Questions from data interns to the recipe team")
public class InternMessageController {

    private final InternMessageService service;

    @GetMapping
    public ResponseEntity<InternInboxResponse> inbox(Authentication auth) {
        return ResponseEntity.ok(service.inbox(auth.getName()));
    }

    @Operation(summary = "Send a new message (optional related assignment and PNG/JPG/PDF attachment up to 10 MB)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InternInboxResponse.Thread> send(@RequestParam String subject,
                                                           @RequestParam(required = false) UUID assignmentId,
                                                           @RequestParam String message,
                                                           @RequestPart(value = "attachment", required = false) MultipartFile attachment,
                                                           Authentication auth) {
        return ResponseEntity.ok(service.send(auth.getName(), subject, assignmentId, message, attachment));
    }

    @PostMapping("/{id}/reply")
    public ResponseEntity<InternInboxResponse.Thread> reply(@PathVariable UUID id, @Valid @RequestBody TextRequest body, Authentication auth) {
        return ResponseEntity.ok(service.reply(auth.getName(), id, body.body()));
    }
}
