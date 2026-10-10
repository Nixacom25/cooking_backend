package com.cooked.backend.service;

import com.cooked.backend.dto.response.InternInboxResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/** Data interns' questions to the recipe team (answered from the admin Support screen). */
public interface InternMessageService {

    InternInboxResponse inbox(String internEmail);

    /**
     * New thread. {@code assignmentId} must be one of the intern's own
     * assignments; the attachment (PNG, JPG or PDF, max 10 MB) is optional.
     */
    InternInboxResponse.Thread send(String internEmail, String subject, UUID assignmentId, String message, MultipartFile attachment);

    InternInboxResponse.Thread reply(String internEmail, UUID threadId, String body);
}
