package com.cooked.backend.controller;

import com.cooked.backend.dto.request.SupportMacroRequest;
import com.cooked.backend.dto.request.TextRequest;
import com.cooked.backend.dto.response.SupportMacroResponse;
import com.cooked.backend.dto.response.TicketMessageResponse;
import com.cooked.backend.service.SupportConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Support conversation (replies, notes) and macros. HTTP only. */
@RestController
@RequestMapping("/api/admin/support")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Support", description = "Ticket replies, internal notes and saved replies")
public class AdminSupportController {

    private final SupportConversationService support;

    @GetMapping("/tickets/{id}/messages")
    public ResponseEntity<List<TicketMessageResponse>> messages(@PathVariable UUID id) {
        return ResponseEntity.ok(support.messages(id));
    }

    @Operation(summary = "Reply to the customer by email (stored on the ticket)")
    @PostMapping("/tickets/{id}/reply")
    public ResponseEntity<TicketMessageResponse> reply(@PathVariable UUID id, @Valid @RequestBody TextRequest body, Authentication auth) {
        return ResponseEntity.ok(support.reply(id, body.body(), auth.getName()));
    }

    @Operation(summary = "Internal note (never sent)")
    @PostMapping("/tickets/{id}/notes")
    public ResponseEntity<TicketMessageResponse> note(@PathVariable UUID id, @Valid @RequestBody TextRequest body, Authentication auth) {
        return ResponseEntity.ok(support.note(id, body.body(), auth.getName()));
    }

    @GetMapping("/macros")
    public ResponseEntity<List<SupportMacroResponse>> macros() {
        return ResponseEntity.ok(support.macros());
    }

    @PostMapping("/macros")
    public ResponseEntity<SupportMacroResponse> createMacro(@Valid @RequestBody SupportMacroRequest body, Authentication auth) {
        return ResponseEntity.ok(support.createMacro(body, auth.getName()));
    }

    @PutMapping("/macros/{id}")
    public ResponseEntity<SupportMacroResponse> updateMacro(@PathVariable UUID id, @Valid @RequestBody SupportMacroRequest body) {
        return ResponseEntity.ok(support.updateMacro(id, body));
    }

    @DeleteMapping("/macros/{id}")
    public ResponseEntity<Void> deleteMacro(@PathVariable UUID id) {
        support.deleteMacro(id);
        return ResponseEntity.noContent().build();
    }
}
