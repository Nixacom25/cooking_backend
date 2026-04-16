package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.dto.response.SessionResponse;
import com.cooked.backend.entity.BlacklistedToken;
import com.cooked.backend.entity.DeviceSession;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.BlacklistedTokenRepository;
import com.cooked.backend.repository.DeviceSessionRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private final DeviceSessionRepository deviceSessionRepository;
    private final UserRepository userRepository;
    private final BlacklistedTokenRepository blacklistedTokenRepository;

    @Override
    public List<SessionResponse> getMySessions(String email, String currentToken) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<DeviceSession> sessions = deviceSessionRepository.findByUserOrderByLastActiveDesc(user);

        return sessions.stream().map(session -> SessionResponse.builder()
                .id(session.getId())
                .deviceName(session.getDeviceName())
                .location(session.getLocation())
                .ipAddress(session.getIpAddress())
                .lastActive(session.getLastActive())
                .isCurrentSession(session.getToken().equals(currentToken))
                .build()).collect(Collectors.toList());
    }

    @Override
    public MessageResponse revokeSession(String email, String sessionId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        DeviceSession session = deviceSessionRepository.findById(UUID.fromString(sessionId))
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));

        if (!session.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized to revoke this session");
        }

        // Add to blacklist
        if (!blacklistedTokenRepository.existsByToken(session.getToken())) {
            BlacklistedToken blacklistedToken = BlacklistedToken.builder()
                    .token(session.getToken())
                    .blacklistedAt(LocalDateTime.now())
                    .build();
            blacklistedTokenRepository.save(blacklistedToken);
        }

        deviceSessionRepository.delete(session);

        return new MessageResponse("Session revoked successfully");
    }
}
