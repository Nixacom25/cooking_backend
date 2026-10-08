package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** An ingredient name seen in the app that no visual (name, canonical id or alias) resolves. */
@Entity
@Table(name = "unmatched_ingredients", indexes = {
        @Index(name = "idx_unmatched_ingredients_status_last", columnList = "status, last_seen_at")})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnmatchedIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name_key", nullable = false, unique = true, length = 120)
    private String nameKey;

    /** The name exactly as it was last returned (scan, import or grocery). */
    @Column(name = "sample_name", nullable = false, length = 120)
    private String sampleName;

    @Column(name = "seen_count", nullable = false)
    private long seenCount;

    @Column(name = "scan_count", nullable = false)
    private long scanCount;

    @Column(name = "import_count", nullable = false)
    private long importCount;

    @Column(name = "grocery_count", nullable = false)
    private long groceryCount;

    @Column(name = "user_count", nullable = false)
    private int userCount;

    @Column(name = "first_seen_at", nullable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private UnmatchedStatus status = UnmatchedStatus.OPEN;

    @Column(name = "resolved_visual_id")
    private UUID resolvedVisualId;

    private LocalDateTime resolvedAt;

    @Column(length = 120)
    private String resolvedBy;

    public void count(IngredientNameSource source) {
        seenCount++;
        switch (source) {
            case SCAN -> scanCount++;
            case IMPORT -> importCount++;
            case GROCERY -> groceryCount++;
        }
    }

    /** Source with the most sightings (scan first on ties). */
    public IngredientNameSource mainSource() {
        if (scanCount >= importCount && scanCount >= groceryCount) return IngredientNameSource.SCAN;
        return importCount >= groceryCount ? IngredientNameSource.IMPORT : IngredientNameSource.GROCERY;
    }
}
