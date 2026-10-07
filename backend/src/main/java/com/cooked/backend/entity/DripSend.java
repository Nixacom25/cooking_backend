package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** One drip message sent to a user (unique per step: a user never gets the same step twice). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "drip_sends", uniqueConstraints = @UniqueConstraint(name = "uk_drip_send", columnNames = {"user_id", "step"}))
public class DripSend {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    /** DAY3 or DAY7. */
    @Column(nullable = false, length = 16)
    private String step;

    @Column(nullable = false, length = 16)
    private String channel;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
