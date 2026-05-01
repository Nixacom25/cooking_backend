package com.cooked.backend.service;

import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.dto.response.SessionResponse;

import java.util.List;

public interface SessionService {
    List<SessionResponse> getMySessions(String email, String currentToken);

    MessageResponse revokeSession(String email, String sessionId);
}
