package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.TicketTriageRequest;
import com.cooked.backend.entity.Role;
import com.cooked.backend.entity.SupportTicket;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.SupportTicketRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.SupportTriageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupportTriageServiceImpl implements SupportTriageService {

    private final SupportTicketRepository tickets;
    private final UserRepository users;

    @Override
    @Transactional
    public SupportTicket triage(UUID ticketId, TicketTriageRequest request) {
        SupportTicket ticket = tickets.findById(ticketId).orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        if (request.assignee() != null) {
            String email = request.assignee().trim().toLowerCase(Locale.ROOT);
            if (email.isEmpty()) {
                ticket.setAssignee(null);
            } else {
                boolean admin = users.findByEmail(email).map(u -> u.getRole() == Role.ADMIN).orElse(false);
                if (!admin) throw new BadRequestException("Tickets can only be assigned to an admin.");
                ticket.setAssignee(email);
            }
        }
        if (request.priority() != null) ticket.setPriority(request.priority());
        return tickets.save(ticket);
    }
}
