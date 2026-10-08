package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** An admin alert marked as read (alert keys are computed by the backoffice, e.g. "ticket:<id>"). */
@Entity
@Table(name = "alert_acks", uniqueConstraints = @UniqueConstraint(name = "uk_alert_ack_key", columnNames = "alert_key"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertAck {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "alert_key", nullable = false, length = 200)
    private String alertKey;

    @Column(length = 160)
    private String ackBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime ackAt;
}
