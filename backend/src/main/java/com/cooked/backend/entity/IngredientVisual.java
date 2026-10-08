package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Art and animation of one ingredient in the app (Ingredient Visual Library).
 * canonicalId is permanent: the app references it in published manifests.
 */
@Entity
@Table(name = "ingredient_visuals", indexes = {
        @Index(name = "idx_ingredient_visuals_status", columnList = "status"),
        @Index(name = "idx_ingredient_visuals_category", columnList = "category")})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngredientVisual {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "canonical_id", nullable = false, unique = true, length = 80, updatable = false)
    private String canonicalId;

    @Column(nullable = false, length = 120)
    private String name;

    /** Normalized name, unique so two visuals never answer to the same name. */
    @Column(name = "name_key", nullable = false, unique = true, length = 120)
    private String nameKey;

    @Column(nullable = false, length = 80)
    private String category;

    @Column(length = 80)
    private String collection;

    /** Animation preset; null = the app falls back to produceFloat. */
    @Column(length = 40)
    private String animation;

    @Column(columnDefinition = "TEXT")
    private String svg;

    @Column(name = "svg_hash", length = 16)
    private String svgHash;

    @Column(name = "svg_bytes")
    private Integer svgBytes;

    @Column(length = 40)
    private String archetype;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private IngredientDelivery delivery = IngredientDelivery.CDN;

    @Column(name = "main_color", length = 9)
    private String mainColor;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean reviewed = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private IngredientVisualStatus status = IngredientVisualStatus.MISSING_ASSET;

    @ElementCollection
    @CollectionTable(name = "ingredient_visual_aliases",
            joinColumns = @JoinColumn(name = "visual_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_ingredient_alias_key", columnNames = "alias_key"))
    @BatchSize(size = 50)
    @Builder.Default
    private List<IngredientAlias> aliases = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(length = 120)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        status = deriveStatus();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    /** Call after every admin change: recomputes the status and stamps who changed it. */
    public void touch(String by) {
        status = deriveStatus();
        updatedAt = LocalDateTime.now();
        updatedBy = by;
    }

    public IngredientVisualStatus deriveStatus() {
        if (!enabled) return IngredientVisualStatus.DISABLED;
        if (svg == null || svg.isBlank()) return IngredientVisualStatus.MISSING_ASSET;
        if (animation == null || animation.isBlank()) return IngredientVisualStatus.MISSING_ANIMATION;
        if (!reviewed) return IngredientVisualStatus.NEEDS_REVIEW;
        return IngredientVisualStatus.COMPLETE;
    }
}
