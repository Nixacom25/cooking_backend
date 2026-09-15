package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Audit log of how each recipe's image was decided - which path was taken
 * (library / edamam / fallback), what was chosen, and the confidence score
 * when a vision model was involved. Doubles as the future admin review queue.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "recipe_image_matches", indexes = {
        @Index(name = "idx_rim_recipe", columnList = "recipe_id")
})
public class RecipeImageMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String tagsUsed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImageMatchPath path;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String chosenImageUrl;

    @Column(nullable = true)
    private Double confidence;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
