package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.InternInboxResponse;
import com.cooked.backend.entity.*;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.RecipeAssignmentRepository;
import com.cooked.backend.repository.SupportTicketRepository;
import com.cooked.backend.repository.TicketMessageRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.CloudinaryService;
import com.cooked.backend.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternMessageServiceImplTest {

    @Mock private UserRepository users;
    @Mock private SupportTicketRepository tickets;
    @Mock private TicketMessageRepository messages;
    @Mock private RecipeAssignmentRepository assignments;
    @Mock private EmailService emailService;
    @Mock private CloudinaryService cloudinary;
    @InjectMocks private InternMessageServiceImpl service;

    private User amina;

    @BeforeEach
    void setUp() {
        amina = User.builder().id(UUID.randomUUID()).email("amina@x.com").firstname("Amina").lastname("K").role(Role.EDITOR).build();
        lenient().when(users.findByEmail("amina@x.com")).thenReturn(Optional.of(amina));
        lenient().when(tickets.save(any())).thenAnswer(i -> {
            SupportTicket t = i.getArgument(0);
            if (t.getId() == null) t.setId(UUID.randomUUID());
            return t;
        });
    }

    @Test
    void sendCreatesAnInternTicketLinkedToOwnRecipe() {
        Recipe r = new Recipe();
        r.setName("Garlic Butter Shrimp");
        RecipeAssignment a = RecipeAssignment.builder().id(UUID.randomUUID()).assignedToUser(amina).recipe(r).build();
        when(assignments.findById(a.getId())).thenReturn(Optional.of(a));

        InternInboxResponse.Thread t = service.send("amina@x.com", "Question about a recipe", a.getId(), "2 tbsp or leave it out?", null);

        assertEquals("Question about a recipe", t.subject());
        assertEquals("Garlic Butter Shrimp", t.recipeName());
        assertEquals("WAITING_REPLY", t.status());
        verify(tickets).save(argThat(x -> "INTERN".equals(x.getSource()) && amina.getId().toString().equals(x.getUserId())));
        verify(emailService).sendSupportNotificationToTeam(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void cannotLinkSomeoneElsesRecipeOrUseAFreeSubject() {
        User other = User.builder().id(UUID.randomUUID()).build();
        RecipeAssignment a = RecipeAssignment.builder().id(UUID.randomUUID()).assignedToUser(other).build();
        when(assignments.findById(a.getId())).thenReturn(Optional.of(a));
        assertThrows(BadRequestException.class, () -> service.send("amina@x.com", "My workload", a.getId(), "hi", null));
        assertThrows(BadRequestException.class, () -> service.send("amina@x.com", "Free text subject", null, "hi", null));
    }

    @Test
    void attachmentMustBeAnImageOrPdf() {
        MockMultipartFile exe = new MockMultipartFile("attachment", "x.exe", "application/octet-stream", new byte[]{1});
        assertThrows(BadRequestException.class, () -> service.send("amina@x.com", "Something else", null, "see file", exe));
        verifyNoInteractions(cloudinary);
    }

    @Test
    void inboxHidesInternalNotesAndShowsAnswered() {
        SupportTicket t = SupportTicket.builder().id(UUID.randomUUID()).source("INTERN").userId(amina.getId().toString())
                .subject("My workload").category("Intern · My workload").message("Off Friday").status("WAITING").build();
        when(tickets.findAllBySourceAndUserIdOrderByCreatedAtDesc("INTERN", amina.getId().toString())).thenReturn(List.of(t));
        when(messages.findByTicketIdOrderByCreatedAtAsc(t.getId())).thenReturn(List.of(
                TicketMessage.builder().kind("NOTE").body("internal: she's late").author("cheikh@x.com").build(),
                TicketMessage.builder().kind("REPLY").body("Sure, moved.").author("cheikh@x.com").build()));
        when(users.findByEmail("cheikh@x.com")).thenReturn(Optional.of(User.builder().email("cheikh@x.com").firstname("Cheikh").lastname("Gueye").build()));
        when(assignments.findAllByAssignedToUserIdOrderByAssignedDateDesc(any(), any())).thenReturn(new PageImpl<>(List.of()));

        InternInboxResponse inbox = service.inbox("amina@x.com");

        InternInboxResponse.Thread th = inbox.threads().get(0);
        assertEquals("ANSWERED", th.status());
        assertEquals(2, th.messages().size());
        assertTrue(th.messages().stream().noneMatch(m -> m.body().contains("internal")));
        assertEquals("Cheikh G.", th.messages().get(1).author());
        assertEquals("Recipe team", inbox.managerName());
    }

    @Test
    void replyOnlyOnOwnThreadAndReopensIt() {
        SupportTicket mine = SupportTicket.builder().id(UUID.randomUUID()).source("INTERN").userId(amina.getId().toString()).status("WAITING").build();
        SupportTicket notMine = SupportTicket.builder().id(UUID.randomUUID()).source("INTERN").userId(UUID.randomUUID().toString()).build();
        when(tickets.findById(mine.getId())).thenReturn(Optional.of(mine));
        when(tickets.findById(notMine.getId())).thenReturn(Optional.of(notMine));

        service.reply("amina@x.com", mine.getId(), "Thanks!");
        assertEquals("OPEN", mine.getStatus());
        assertThrows(ResourceNotFoundException.class, () -> service.reply("amina@x.com", notMine.getId(), "hi"));
    }
}
