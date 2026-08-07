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
@Table(name = "recipe_assignments")
public class RecipeAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_user_id", nullable = false)
    private User assignedToUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_user_id", nullable = false)
    private User assignedByUser;

    @CreationTimestamp
    @Column(name = "assigned_date", updatable = false)
    private LocalDateTime assignedDate;

    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private AssignmentStatus status = AssignmentStatus.ASSIGNED;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false)
    @Builder.Default
    private AssignmentFrequency frequency = AssignmentFrequency.NONE;

    @Column(name = "completed_date")
    private LocalDateTime completedDate;

    @Column(name = "submitted_date")
    private LocalDateTime submittedDate;

    @Column(name = "validated_date")
    private LocalDateTime validatedDate;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "recipe_assignment_error_categories", joinColumns = @JoinColumn(name = "assignment_id"))
    @Column(name = "error_category")
    @Builder.Default
    private List<String> errorCategories = new ArrayList<>();

    @Column(name = "feedback_comment", columnDefinition = "TEXT")
    private String feedbackComment;

    @Column(name = "revision_count")
    @Builder.Default
    private Integer revisionCount = 0;
}
