package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One time an automation fired (for a user, or for the team when userId is null): used to fire once per user. */
@Entity
@Table(name = "custom_automation_fires", indexes = {
        @Index(name = "idx_auto_fire_user", columnList = "automationId, userId"),
        @Index(name = "idx_auto_fire_time", columnList = "automationId, firedAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomAutomationFire {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID automationId;

    private UUID userId;

    @Column(nullable = false)
    private LocalDateTime firedAt;

    private boolean pushSent;
    private boolean emailSent;
}
