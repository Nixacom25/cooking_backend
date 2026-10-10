package com.cooked.backend.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * "Contact your manager" for a data intern: who answers and their threads.
 * Threads are support tickets (source INTERN); internal notes are never included.
 */
public record InternInboxResponse(String managerName, List<Thread> threads) {

    /** status: WAITING_REPLY (the team hasn't answered the last message), ANSWERED or CLOSED. */
    public record Thread(UUID id, String subject, String recipeName, UUID assignmentId, String status,
                         LocalDateTime createdAt, String attachmentUrl, List<Message> messages) {
    }

    public record Message(String author, boolean mine, String body, LocalDateTime createdAt) {
    }
}
