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
 * Critical error reports from mobile app for monitoring and alerting
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "critical_errors")
public class CriticalError {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String errorType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String errorMessage;

    @Column(columnDefinition = "TEXT")
    private String stackTrace;

    @Column(length = 255)
    private String userId;

    @Column(length = 255)
    private String userEmail;

    @Column(length = 50)
    private String platform;

    @Column(length = 100)
    private String osVersion;

    @Column(length = 50)
    private String appVersion;

    @Column(columnDefinition = "TEXT")
    private String context;

    @Column(nullable = false)
    @Builder.Default
    private String status = "NEW";

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}