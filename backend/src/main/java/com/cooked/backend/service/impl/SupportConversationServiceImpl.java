package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.SupportMacroRequest;
import com.cooked.backend.dto.response.SupportMacroResponse;
import com.cooked.backend.dto.response.TicketMessageResponse;
import com.cooked.backend.entity.SupportMacro;
import com.cooked.backend.entity.SupportTicket;
import com.cooked.backend.entity.TicketMessage;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.SupportMacroRepository;
import com.cooked.backend.repository.SupportTicketRepository;
import com.cooked.backend.repository.TicketMessageRepository;
import com.cooked.backend.service.EmailService;
import com.cooked.backend.service.SupportConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupportConversationServiceImpl implements SupportConversationService {

    private static final Set<String> AWAITING_TEAM = Set.of("NEW", "OPEN", "ASSIGNED", "IN_PROGRESS");

    private final SupportTicketRepository tickets;
    private final TicketMessageRepository messages;
    private final SupportMacroRepository macros;
    private final EmailService emailService;

    @Override
    @Transactional(readOnly = true)
    public List<TicketMessageResponse> messages(UUID ticketId) {
        ticket(ticketId);
        return messages.findByTicketIdOrderByCreatedAtAsc(ticketId).stream().map(SupportConversationServiceImpl::view).toList();
    }

    @Override
    @Transactional
    public TicketMessageResponse reply(UUID ticketId, String body, String adminEmail) {
        SupportTicket t = ticket(ticketId);
        if (t.getEmail() == null || t.getEmail().isBlank()) throw new BadRequestException("This ticket has no email address to reply to.");
        String text = body.trim();
        emailService.sendSupportReplyEmail(t.getEmail(), t.getName(), t.getId().toString().substring(0, 8).toUpperCase(), t.getSubject(), text);
        if (t.getFirstResponseAt() == null) t.setFirstResponseAt(LocalDateTime.now());
        if (t.getStatus() == null || AWAITING_TEAM.contains(t.getStatus().toUpperCase())) t.setStatus("WAITING");
        tickets.save(t);
        return view(messages.save(TicketMessage.builder().ticketId(ticketId).kind("REPLY").body(text).author(adminEmail).build()));
    }

    @Override
    @Transactional
    public TicketMessageResponse note(UUID ticketId, String body, String adminEmail) {
        ticket(ticketId);
        return view(messages.save(TicketMessage.builder().ticketId(ticketId).kind("NOTE").body(body.trim()).author(adminEmail).build()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupportMacroResponse> macros() {
        return macros.findAllByOrderByTitleAsc().stream().map(SupportConversationServiceImpl::view).toList();
    }

    @Override
    @Transactional
    public SupportMacroResponse createMacro(SupportMacroRequest r, String adminEmail) {
        return view(macros.save(SupportMacro.builder().title(r.title().trim()).body(r.body().trim()).createdBy(adminEmail).build()));
    }

    @Override
    @Transactional
    public SupportMacroResponse updateMacro(UUID id, SupportMacroRequest r) {
        SupportMacro m = macros.findById(id).orElseThrow(() -> new ResourceNotFoundException("Macro not found"));
        m.setTitle(r.title().trim());
        m.setBody(r.body().trim());
        return view(macros.save(m));
    }

    @Override
    @Transactional
    public void deleteMacro(UUID id) {
        if (!macros.existsById(id)) throw new ResourceNotFoundException("Macro not found");
        macros.deleteById(id);
    }

    private SupportTicket ticket(UUID id) {
        return tickets.findById(id).orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
    }

    static TicketMessageResponse view(TicketMessage m) {
        return new TicketMessageResponse(m.getId(), m.getKind(), m.getBody(), m.getAuthor(), m.getCreatedAt());
    }

    static SupportMacroResponse view(SupportMacro m) {
        return new SupportMacroResponse(m.getId(), m.getTitle(), m.getBody(), m.getCreatedBy(), m.getUpdatedAt() != null ? m.getUpdatedAt() : m.getCreatedAt());
    }
}
