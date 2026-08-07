package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Entity
@Table(name = "recipe_assignment_history")
public class RecipeAssignmentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private RecipeAssignment recipeAssignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(name = "action", nullable = false)
    private String action; // e.g., ASSIGNED, STARTED, SUBMITTED_FOR_VALIDATION, NEEDS_CORRECTION, VALIDATED

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status")
    private AssignmentStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false)
    private AssignmentStatus newStatus;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "recipe_history_error_categories", joinColumns = @JoinColumn(name = "history_id"))
    @Column(name = "error_category")
    @Builder.Default
    private List<String> errorCategories = new ArrayList<>();

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
