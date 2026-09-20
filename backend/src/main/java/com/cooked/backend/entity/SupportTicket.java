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
 * A support/feedback submission from the public website contact form.
 * There is no admin reply UI yet - the confirmation email's reply-to lets a
 * human answer over ordinary email threading instead.
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
    private String status = "OPEN";

    /** Where the submission came from: WEB or MOBILE. */
    @Column(nullable = false)
    @Builder.Default
    private String source = "WEB";

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
