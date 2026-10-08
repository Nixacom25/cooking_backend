package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** Filters saved by an admin on a backoffice screen (query = JSON of the filter values). */
@Entity
@Table(name = "saved_views", indexes = @Index(name = "idx_saved_views_owner_screen", columnList = "owner, screen"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavedView {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 160)
    private String owner;

    @Column(nullable = false, length = 60)
    private String screen;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String query;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
