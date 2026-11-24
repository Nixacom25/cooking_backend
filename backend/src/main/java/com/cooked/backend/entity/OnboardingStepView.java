package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * First time an app install saw one onboarding screen (Admin → Onboarding funnel).
 * One row per install and screen; install = hashed random id kept on the device (no personal data).
 */
@Entity
@Table(name = "onboarding_step_views",
        uniqueConstraints = @UniqueConstraint(name = "uk_onboarding_step_install", columnNames = {"install_id", "step"}),
        indexes = {
                @Index(name = "idx_onboarding_step_created", columnList = "step, created_at"),
                @Index(name = "idx_onboarding_install", columnList = "install_id")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingStepView {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "install_id", nullable = false, length = 64)
    private String installId;

    /** Page index in the app's onboarding (0 = first screen). */
    @Column(nullable = false)
    private int step;

    @Column(nullable = false, length = 40)
    private String screen;

    /** ios / android / other. */
    @Column(nullable = false, length = 16)
    private String platform;

    @Column(length = 32)
    private String appVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
