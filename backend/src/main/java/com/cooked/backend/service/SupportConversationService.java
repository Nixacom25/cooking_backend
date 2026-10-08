package com.cooked.backend.service;

import com.cooked.backend.dto.request.SupportMacroRequest;
import com.cooked.backend.dto.response.SupportMacroResponse;
import com.cooked.backend.dto.response.TicketMessageResponse;

import java.util.List;
import java.util.UUID;

/** Replies, internal notes and saved replies (macros) of support tickets. */
public interface SupportConversationService {

    List<TicketMessageResponse> messages(UUID ticketId);

    /** Emails the customer, stores the reply, sets the first response time and moves NEW tickets to WAITING. */
    TicketMessageResponse reply(UUID ticketId, String body, String adminEmail);

    /** Internal note, never sent to the customer. */
    TicketMessageResponse note(UUID ticketId, String body, String adminEmail);

    List<SupportMacroResponse> macros();

    SupportMacroResponse createMacro(SupportMacroRequest request, String adminEmail);

    SupportMacroResponse updateMacro(UUID id, SupportMacroRequest request);

    void deleteMacro(UUID id);
}
