package com.cooked.backend.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CustomRoleResponse(UUID id, String name, String description, Map<String, String> permissions,
                                 List<Member> members, String createdBy, LocalDateTime createdAt) {

    public record Member(UUID id, String name, String email) {
    }
}
