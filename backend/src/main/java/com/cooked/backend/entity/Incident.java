package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** Incident declared by an admin from System health (OPEN → MONITORING → RESOLVED). */
@Entity
@Table(name = "incidents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String message;

    /** MINOR, MAJOR or CRITICAL. */
    @Column(nullable = false, length = 16)
    private String severity;

    /** OPEN, MONITORING or RESOLVED. */
    @Column(nullable = false, length = 16)
    private String status;

    @Column(length = 160)
    private String createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;
}
