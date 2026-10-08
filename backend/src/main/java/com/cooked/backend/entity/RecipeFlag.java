package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** A problem reported on a recipe (shown in Recipe detail → Reports) until resolved. */
@Entity
@Table(name = "recipe_flags", indexes = @Index(name = "idx_recipe_flags_recipe", columnList = "recipe_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "recipe_id", nullable = false)
    private UUID recipeId;

    /** WRONG_INFO, BAD_IMAGE, DUPLICATE, INAPPROPRIATE, COPYRIGHT or OTHER. */
    @Column(nullable = false, length = 20)
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(length = 160)
    private String createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;

    @Column(length = 160)
    private String resolvedBy;
}
