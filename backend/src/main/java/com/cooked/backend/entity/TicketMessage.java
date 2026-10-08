package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** A message on a support ticket: REPLY (emailed to the customer) or NOTE (internal, never sent). */
@Entity
@Table(name = "ticket_messages", indexes = @Index(name = "idx_ticket_messages_ticket", columnList = "ticket_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    /** REPLY or NOTE. */
    @Column(nullable = false, length = 8)
    private String kind;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(length = 160)
    private String author;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
