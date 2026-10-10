package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.InternInboxResponse;
import com.cooked.backend.entity.RecipeAssignment;
import com.cooked.backend.entity.SupportTicket;
import com.cooked.backend.entity.TicketMessage;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.RecipeAssignmentRepository;
import com.cooked.backend.repository.SupportTicketRepository;
import com.cooked.backend.repository.TicketMessageRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.CloudinaryService;
import com.cooked.backend.service.EmailService;
import com.cooked.backend.service.InternMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InternMessageServiceImpl implements InternMessageService {

    static final String SOURCE = "INTERN";
    static final Set<String> SUBJECTS = Set.of("Question about a recipe", "Question about a correction", "My workload", "Something else");
    private static final Set<String> ATTACHMENT_TYPES = Set.of("image/png", "image/jpeg", "application/pdf");
    private static final long MAX_ATTACHMENT = 10L * 1024 * 1024;
    private static final Set<String> CLOSED = Set.of("CLOSED", "RESOLVED", "DONE");

    private final UserRepository users;
    private final SupportTicketRepository tickets;
    private final TicketMessageRepository messages;
    private final RecipeAssignmentRepository assignments;
    private final EmailService emailService;
    private final CloudinaryService cloudinary;

    @Value("${support.notification.email:contact@cookedapp.com}")
    private String teamEmail;

    @Override
    @Transactional(readOnly = true)
    public InternInboxResponse inbox(String internEmail) {
        User me = user(internEmail);
        List<InternInboxResponse.Thread> threads = tickets.findAllBySourceAndUserIdOrderByCreatedAtDesc(SOURCE, me.getId().toString())
                .stream().map(t -> view(t, me)).toList();
        return new InternInboxResponse(managerName(me), threads);
    }

    @Override
    @Transactional
    public InternInboxResponse.Thread send(String internEmail, String subject, UUID assignmentId, String message, MultipartFile attachment) {
        User me = user(internEmail);
        String topic = subject == null ? "" : subject.trim();
        if (!SUBJECTS.contains(topic)) throw new BadRequestException("Choose a subject from the list.");
        String text = message == null ? "" : message.trim();
        if (text.isEmpty()) throw new BadRequestException("Write your message.");
        if (text.length() > 4000) throw new BadRequestException("Keep the message under 4,000 characters.");

        String recipeName = null;
        if (assignmentId != null) {
            RecipeAssignment a = assignments.findById(assignmentId)
                    .filter(x -> x.getAssignedToUser() != null && Objects.equals(x.getAssignedToUser().getId(), me.getId()))
                    .orElseThrow(() -> new BadRequestException("Pick one of your own assigned recipes."));
            recipeName = a.getRecipe() != null ? a.getRecipe().getName() : null;
        }

        String attachmentUrl = null;
        if (attachment != null && !attachment.isEmpty()) {
            if (attachment.getSize() > MAX_ATTACHMENT) throw new BadRequestException("The file is larger than 10 MB.");
            if (!ATTACHMENT_TYPES.contains(String.valueOf(attachment.getContentType()).toLowerCase())) {
                throw new BadRequestException("Attach a PNG, JPG or PDF file.");
            }
            try {
                attachmentUrl = cloudinary.upload(attachment);
            } catch (Exception e) {
                throw new BadRequestException("The file couldn't be uploaded. Try again.");
            }
        }

        String fullName = name(me);
        SupportTicket saved = tickets.save(SupportTicket.builder()
                .name(fullName)
                .email(me.getEmail())
                .subject(recipeName == null ? topic : topic + " · " + recipeName)
                .message(text)
                .source(SOURCE)
                .category("Intern · " + topic)
                .userId(me.getId().toString())
                .relatedAssignmentId(assignmentId)
                .attachmentUrl(attachmentUrl)
                .build());
        notifyTeam(saved, text);
        return view(saved, me);
    }

    @Override
    @Transactional
    public InternInboxResponse.Thread reply(String internEmail, UUID threadId, String body) {
        User me = user(internEmail);
        SupportTicket t = tickets.findById(threadId)
                .filter(x -> SOURCE.equals(x.getSource()) && me.getId().toString().equals(x.getUserId()))
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        String text = body == null ? "" : body.trim();
        if (text.isEmpty()) throw new BadRequestException("Write your reply.");
        if (text.length() > 4000) throw new BadRequestException("Keep the reply under 4,000 characters.");
        messages.save(TicketMessage.builder().ticketId(t.getId()).kind(SOURCE).body(text).author(me.getEmail()).build());
        // Back in the team's queue.
        t.setStatus("OPEN");
        tickets.save(t);
        notifyTeam(t, text);
        return view(t, me);
    }

    private void notifyTeam(SupportTicket t, String text) {
        try {
            emailService.sendSupportNotificationToTeam(teamEmail, t.getId().toString().substring(0, 8).toUpperCase(), t.getSubject(),
                    t.getName(), t.getEmail(), text, t.getUserId(), "Backoffice", null, t.getCategory());
        } catch (Exception e) {
            log.warn("Intern message notification failed: {}", e.getMessage());
        }
    }

    private InternInboxResponse.Thread view(SupportTicket t, User me) {
        List<InternInboxResponse.Message> thread = new ArrayList<>();
        thread.add(new InternInboxResponse.Message(name(me), true, t.getMessage(), t.getCreatedAt()));
        String lastKind = SOURCE;
        for (TicketMessage m : messages.findByTicketIdOrderByCreatedAtAsc(t.getId())) {
            if ("NOTE".equalsIgnoreCase(m.getKind())) continue; // internal notes stay internal
            boolean mine = SOURCE.equalsIgnoreCase(m.getKind());
            thread.add(new InternInboxResponse.Message(mine ? name(me) : teamName(m.getAuthor()), mine, m.getBody(), m.getCreatedAt()));
            lastKind = mine ? SOURCE : "REPLY";
        }
        String st = t.getStatus() == null ? "" : t.getStatus().toUpperCase();
        String status = CLOSED.contains(st) ? "CLOSED" : "REPLY".equals(lastKind) ? "ANSWERED" : "WAITING_REPLY";
        String recipeName = null;
        if (t.getRelatedAssignmentId() != null && t.getSubject() != null && t.getSubject().contains(" · ")) {
            recipeName = t.getSubject().substring(t.getSubject().indexOf(" · ") + 3);
        }
        String subject = t.getCategory() != null && t.getCategory().startsWith("Intern · ") ? t.getCategory().substring(9) : t.getSubject();
        return new InternInboxResponse.Thread(t.getId(), subject, recipeName, t.getRelatedAssignmentId(), status,
                t.getCreatedAt(), t.getAttachmentUrl(), thread);
    }

    /** The admin who assigned the intern's latest recipe, else the team. */
    private String managerName(User me) {
        return assignments.findAllByAssignedToUserIdOrderByAssignedDateDesc(me.getId(), PageRequest.of(0, 1)).stream()
                .map(RecipeAssignment::getAssignedByUser).filter(Objects::nonNull).findFirst()
                .map(InternMessageServiceImpl::shortName).orElse("Recipe team");
    }

    private String teamName(String email) {
        if (email == null) return "Recipe team";
        return users.findByEmail(email).map(InternMessageServiceImpl::shortName).orElse("Recipe team");
    }

    static String shortName(User u) {
        String first = u.getFirstname() == null ? "" : u.getFirstname().trim();
        String last = u.getLastname() == null || u.getLastname().isBlank() ? "" : u.getLastname().trim().charAt(0) + ".";
        String n = (first + " " + last).trim();
        return n.isEmpty() ? u.getEmail() : n;
    }

    private static String name(User u) {
        String n = ((u.getFirstname() == null ? "" : u.getFirstname()) + " " + (u.getLastname() == null ? "" : u.getLastname())).trim();
        return n.isEmpty() ? u.getEmail() : n;
    }

    private User user(String email) {
        return users.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
