package com.cooked.backend.dto.request;

import com.cooked.backend.entity.AssignmentFrequency;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class CreateAssignmentRequest {
    private List<UUID> recipeIds;
    private List<UUID> userIds;
    private LocalDateTime dueDate;
    private AssignmentFrequency frequency;
    /** If true, any existing active assignment for the recipe will be cancelled and replaced. */
    private Boolean forceReassign;
}

