package com.cooked.backend.dto.response;

import com.cooked.backend.entity.AssignmentFrequency;
import com.cooked.backend.entity.AssignmentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class RecipeAssignmentResponse {
    private UUID id;
    private RecipeResponse recipe;
    private CreatorResponse assignedToUser;
    private CreatorResponse assignedByUser;
    private LocalDateTime assignedDate;
    private LocalDateTime dueDate;
    private AssignmentStatus status;
    private AssignmentFrequency frequency;
    private LocalDateTime completedDate;
}
