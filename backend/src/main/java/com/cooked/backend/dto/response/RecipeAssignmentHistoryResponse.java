package com.cooked.backend.dto.response;

import com.cooked.backend.entity.AssignmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeAssignmentHistoryResponse {
    private UUID id;
    private CreatorResponse actor;
    private String action;
    private AssignmentStatus previousStatus;
    private AssignmentStatus newStatus;
    private List<String> errorCategories;
    private String comment;
    private LocalDateTime createdAt;
}
