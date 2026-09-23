package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A support/feedback submission from the public website contact form or mobile app.
 * Updated to include detailed user information for better support tracking.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "support_tickets")
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false)
    @Builder.Default
    private String status = "NEW";

    /** Where the submission came from: WEB or MOBILE. */
    @Column(nullable = false)
    @Builder.Default
    private String source = "WEB";

    /** User ID (for mobile app submissions when user is logged in) */
    private String userId;

    /** Device/Platform information (e.g., "iOS", "Android", "Web") */
    private String platform;

    /** App version (for mobile app submissions) */
    private String appVersion;

    /** Category for better organization (Account, Payment, Scan, Import, Recipe, Shopping, Other) */
    private String category;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
