package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Anonymous trace of a deleted account: only a SHA-256 of the normalized email, so a person coming back
 * (even years later) is recognised and does not get a second free trial. No other personal data.
 */
@Entity
@Table(name = "former_accounts", indexes = @Index(name = "idx_former_accounts_hash", columnList = "email_hash", unique = true))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormerAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "email_hash", nullable = false, length = 64, unique = true)
    private String emailHash;

    @CreationTimestamp
    @Column(name = "deleted_at", nullable = false, updatable = false)
    private LocalDateTime deletedAt;
}
