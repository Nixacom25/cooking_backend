package com.cooked.backend.controller;

import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.dto.response.SessionResponse;
import com.cooked.backend.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
@Tag(name = "Session Management", description = "Endpoints for viewing and revoking active sessions")
@SecurityRequirement(name = "bearerAuth")
public class SessionController {

    private final SessionService sessionService;

    @Operation(summary = "Get my active sessions")
    @GetMapping
    public ResponseEntity<List<SessionResponse>> getMySessions(Authentication auth,
            @RequestHeader("Authorization") String authHeader) {
        String currentToken = authHeader.substring(7);
        return ResponseEntity.ok(sessionService.getMySessions(auth.getName(), currentToken));
    }

    @Operation(summary = "Revoke a session")
    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> revokeSession(Authentication auth, @PathVariable String id) {
        return ResponseEntity.ok(sessionService.revokeSession(auth.getName(), id));
    }
}
