package com.cooked.backend.service.impl;

import com.cooked.backend.entity.SupportTicket;
import com.cooked.backend.entity.TicketMessage;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.repository.SupportMacroRepository;
import com.cooked.backend.repository.SupportTicketRepository;
import com.cooked.backend.repository.TicketMessageRepository;
import com.cooked.backend.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupportConversationServiceImplTest {

    @Mock private SupportTicketRepository tickets;
    @Mock private TicketMessageRepository messages;
    @Mock private SupportMacroRepository macros;
    @Mock private EmailService email;
    @InjectMocks private SupportConversationServiceImpl service;

    private final UUID id = UUID.fromString("3ed7aaaa-0000-0000-0000-000000000001");

    @Test
    void replyEmailsStoresAndSetsFirstResponse() {
        SupportTicket t = SupportTicket.builder().id(id).email("ana@x.com").name("Ana").subject("Login").status("NEW").build();
        when(tickets.findById(id)).thenReturn(Optional.of(t));
        when(messages.save(any())).thenAnswer(i -> i.getArgument(0));

        var m = service.reply(id, "  Hello Ana  ", "boss@cooked.app");

        verify(email).sendSupportReplyEmail("ana@x.com", "Ana", "3ED7AAAA", "Login", "Hello Ana");
        assertEquals("REPLY", m.kind());
        assertEquals("WAITING", t.getStatus());
        assertNotNull(t.getFirstResponseAt());

        LocalDateTime first = t.getFirstResponseAt();
        t.setStatus("RESOLVED");
        service.reply(id, "again", "boss@cooked.app");
        assertEquals(first, t.getFirstResponseAt());   // first response kept
        assertEquals("RESOLVED", t.getStatus());       // closed tickets keep their status
    }

    @Test
    void noteIsNeverEmailedAndReplyNeedsAnEmail() {
        SupportTicket t = SupportTicket.builder().id(id).subject("x").build();
        when(tickets.findById(id)).thenReturn(Optional.of(t));
        when(messages.save(any())).thenAnswer(i -> i.getArgument(0));
        assertEquals("NOTE", service.note(id, "check RevenueCat", "boss@cooked.app").kind());
        assertThrows(BadRequestException.class, () -> service.reply(id, "hi", "boss@cooked.app"));
        verifyNoInteractions(email);
        verify(messages).save(argThat((TicketMessage x) -> "NOTE".equals(x.getKind())));
    }
}
