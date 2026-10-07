package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** One execution of a scheduled backend job (recorded by ScheduledJobAspect). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "automation_runs", indexes = @Index(name = "idx_automation_runs_job_created", columnList = "job, createdAt"))
public class AutomationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** "TrialReminderService.sendTrialEndingReminders". */
    @Column(nullable = false, length = 120)
    private String job;

    @Column(nullable = false)
    private boolean success;

    private Integer durationMs;

    @Column(length = 160)
    private String detail;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
