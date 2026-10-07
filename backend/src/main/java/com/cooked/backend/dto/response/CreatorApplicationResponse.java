package com.cooked.backend.dto.response;

import com.cooked.backend.entity.CreatorApplicationStatus;
import com.cooked.backend.entity.CreatorProgram;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorApplicationResponse {
    private UUID id;
    private CreatorProgram program;
    private String name;
    private String email;
    private String handle;
    private String platform;
    private String audience;
    private String details;
    private CreatorApplicationStatus status;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private UUID ambassadorId;
    private LocalDateTime createdAt;
}
