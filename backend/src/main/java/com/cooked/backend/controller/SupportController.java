package com.cooked.backend.controller;

import com.cooked.backend.dto.request.SupportTicketRequest;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.entity.SupportTicket;
import com.cooked.backend.repository.SupportTicketRepository;
import com.cooked.backend.service.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

/**
 * Public support/feedback form endpoint - the marketing site's Support page
 * posts here. No auth required, since the site has no user accounts.
 */
@RestController
@RequestMapping("/support")
@RequiredArgsConstructor
@Tag(name = "Support", description = "Public support/feedback submissions")
public class SupportController {

    private final SupportTicketRepository supportTicketRepository;
    private final EmailService emailService;

    @Value("${support.notification.email:contact@cookedapp.com}")
    private String supportTeamEmail;

    @Operation(summary = "Submit a support/feedback request from the public website")
    @PostMapping("/submit")
    public ResponseEntity<MessageResponse> submit(@Valid @RequestBody SupportTicketRequest request) {
        String source = "MOBILE".equalsIgnoreCase(request.getSource()) ? "MOBILE" : "WEB";

        SupportTicket ticket = SupportTicket.builder()
                .name(request.getName().trim())
                .email(request.getEmail().trim())
                .subject(request.getSubject().trim())
                .message(request.getMessage().trim())
                .source(source)
                .userId(request.getUserId())
                .platform(request.getPlatform())
                .appVersion(request.getAppVersion())
                .category(request.getCategory())
                .build();

        SupportTicket saved = supportTicketRepository.save(ticket);
        String ticketNumber = shortTicketNumber(saved.getId());

        emailService.sendSupportRequestReceivedEmail(saved.getEmail(), saved.getName(), ticketNumber, saved.getSubject());
        emailService.sendSupportNotificationToTeam(supportTeamEmail, ticketNumber, saved.getSubject(),
                saved.getName(), saved.getEmail(), saved.getMessage(), saved.getUserId(), saved.getPlatform(), 
                saved.getAppVersion(), saved.getCategory());

        return ResponseEntity.ok(new MessageResponse("Your request has been received. We'll get back to you soon."));
    }

    private String shortTicketNumber(java.util.UUID id) {
        return id.toString().substring(0, 8).toUpperCase();
    }

    // --- Admin-only: view submissions from both the website and the mobile app ---

    @Operation(summary = "List support/feedback tickets (admin only)")
    @GetMapping("/tickets")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<SupportTicket>> listTickets(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(com.cooked.backend.util.PaginationUtils.clampPage(page), com.cooked.backend.util.PaginationUtils.clampSize(size), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<SupportTicket> tickets = (status == null || status.isBlank())
                ? supportTicketRepository.findAllByOrderByCreatedAtDesc(pageable)
                : supportTicketRepository.findAllByStatusOrderByCreatedAtDesc(status.toUpperCase(), pageable);
        return ResponseEntity.ok(tickets);
    }

    @Operation(summary = "Update a ticket's status (admin only)")
    @PutMapping("/tickets/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SupportTicket> updateTicketStatus(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        SupportTicket ticket = supportTicketRepository.findById(id)
                .orElseThrow(() -> new com.cooked.backend.exception.ResourceNotFoundException("Ticket not found"));
        String status = body.get("status");
        if (status != null && !status.isBlank()) {
            ticket.setStatus(status.toUpperCase());
            supportTicketRepository.save(ticket);
        }
        return ResponseEntity.ok(ticket);
    }
}
