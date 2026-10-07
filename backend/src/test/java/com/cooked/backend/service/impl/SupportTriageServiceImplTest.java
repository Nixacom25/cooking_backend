package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.TicketTriageRequest;
import com.cooked.backend.entity.Role;
import com.cooked.backend.entity.SupportTicket;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.repository.SupportTicketRepository;
import com.cooked.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupportTriageServiceImplTest {

    @Mock private SupportTicketRepository tickets;
    @Mock private UserRepository users;
    @InjectMocks private SupportTriageServiceImpl service;

    private final UUID id = UUID.randomUUID();
    private SupportTicket ticket;

    @BeforeEach
    void setUp() {
        ticket = SupportTicket.builder().id(id).subject("Help").build();
        when(tickets.findById(id)).thenReturn(Optional.of(ticket));
        lenient().when(tickets.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void assignsAnAdminAndSetsPriority() {
        when(users.findByEmail("boss@cooked.app")).thenReturn(Optional.of(User.builder().email("boss@cooked.app").role(Role.ADMIN).build()));
        SupportTicket t = service.triage(id, new TicketTriageRequest(" Boss@Cooked.app ", "URGENT"));
        assertEquals("boss@cooked.app", t.getAssignee());
        assertEquals("URGENT", t.getPriority());
    }

    @Test
    void refusesNonAdminsAndUnassignsOnEmpty() {
        when(users.findByEmail("client@x.com")).thenReturn(Optional.of(User.builder().role(Role.CLIENT).build()));
        assertThrows(BadRequestException.class, () -> service.triage(id, new TicketTriageRequest("client@x.com", null)));
        ticket.setAssignee("boss@cooked.app");
        assertNull(service.triage(id, new TicketTriageRequest("", null)).getAssignee());
        verify(tickets, atLeastOnce()).save(ticket);
    }
}
