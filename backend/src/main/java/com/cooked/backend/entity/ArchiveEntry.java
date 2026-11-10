package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Trace of something removed for good (an account, a recipe purged from the archive...).
 * Only a label is kept: deleted accounts keep no personal data beyond a masked email.
 */
@Entity
@Table(name = "archive_entries", indexes = @Index(name = "idx_archive_entries_created", columnList = "created_at"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** CLIENT, CREATOR, ADMIN, EDITOR or RECIPE. */
    @Column(name = "entity_type", nullable = false, length = 20)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(nullable = false, length = 300)
    private String label;

    @Column(columnDefinition = "TEXT")
    private String details;

    /** Email of the person who deleted it, or "self" for an account deleted by its owner. */
    @Column(name = "deleted_by", length = 160)
    private String deletedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
