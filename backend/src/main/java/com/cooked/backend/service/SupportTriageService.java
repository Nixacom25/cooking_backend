package com.cooked.backend.service;

import com.cooked.backend.dto.request.TicketTriageRequest;
import com.cooked.backend.entity.SupportTicket;

import java.util.UUID;

/** Assignee and priority of support tickets (admin). */
public interface SupportTriageService {

    /** @throws com.cooked.backend.exception.BadRequestException when the assignee is not an admin */
    SupportTicket triage(UUID ticketId, TicketTriageRequest request);
}
