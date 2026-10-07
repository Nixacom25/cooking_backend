package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** Application sent from the website's creator / ambassador pages. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "creator_applications", indexes = @Index(name = "idx_creator_applications_status", columnList = "status, createdAt"))
public class CreatorApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CreatorProgram program;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(length = 80)
    private String handle;

    @Column(length = 40)
    private String platform;

    @Column(length = 40)
    private String audience;

    /** Other answers from the form ("Label: value" lines). */
    @Column(length = 4000)
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CreatorApplicationStatus status;

    @Column(length = 120)
    private String reviewedBy;

    private LocalDateTime reviewedAt;

    /** Ambassador created when an ambassador application is approved. */
    private UUID ambassadorId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
