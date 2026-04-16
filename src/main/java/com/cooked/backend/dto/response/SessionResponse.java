package com.cooked.backend.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class SessionResponse {
    private UUID id;
    private String deviceName;
    private String location;
    private String ipAddress;
    private LocalDateTime lastActive;
    private boolean isCurrentSession;
}
