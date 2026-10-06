package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One row per user and per day the app was opened (from the mobile
 * last-active ping). Gives DAU / WAU / MAU history without storing sessions.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_activity_days",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_activity_day", columnNames = {"userId", "day"}),
        indexes = @Index(name = "idx_user_activity_day", columnList = "day"))
public class UserActivityDay {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private LocalDate day;
}
