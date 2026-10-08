package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** A published version of the ingredient catalog; manifest is the JSON the app downloads. */
@Entity
@Table(name = "ingredient_catalog_releases")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngredientCatalogRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private int version;

    @Column(nullable = false)
    private LocalDateTime publishedAt;

    @Column(length = 120)
    private String publishedBy;

    @Column(name = "ingredient_count", nullable = false)
    private int ingredientCount;

    @Column(length = 500)
    private String notes;

    /** Kept for the latest releases only (older ones are cleared to save space). */
    @Column(columnDefinition = "TEXT")
    private String manifest;
}
