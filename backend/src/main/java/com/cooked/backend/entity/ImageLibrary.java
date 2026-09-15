package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A tagged, reusable photo. One row per distinct image (not per recipe) -
 * matching a new recipe is a tag-overlap lookup against this table before
 * ever calling an external image API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "image_library")
public class ImageLibrary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String imageUrl;

    @Column(nullable = true, length = 100)
    private String cuisine;

    @Column(nullable = true, length = 100)
    private String protein;

    @Column(nullable = true, length = 150)
    private String dishType;

    @Column(nullable = true, length = 100)
    private String cookingStyle;

    // Lowercase, comma-separated ingredient names - kept as plain text
    // rather than a join table since it's only ever read/written as a whole.
    @Column(nullable = true, columnDefinition = "TEXT")
    private String ingredients;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImageSource source;

    @Builder.Default
    @Column(nullable = false)
    private long timesUsed = 0;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
