package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A workspace data export requested from Settings → Export data. The file
 * itself lives in {@link DataExportFile} (kept 7 days), so listing exports
 * never loads it.
 */
@Entity
@Table(name = "data_exports", indexes = @Index(name = "idx_data_export_created", columnList = "createdAt"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataExport {

    public enum Status { PENDING, RUNNING, READY, FAILED, EXPIRED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 160)
    private String requestedBy;

    @Column(length = 160)
    private String requestedByName;

    /** Comma-separated dataset keys: users, recipes, payments, costs, audit. */
    @Column(nullable = false, length = 120)
    private String datasets;

    /** CSV or JSON. */
    @Column(nullable = false, length = 8)
    private String format;

    /** First and last day included (null = from the beginning). */
    private LocalDate fromDay;
    private LocalDate toDay;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status;

    /** 0..100 while running. */
    private int progress;

    private Long sizeBytes;

    @Column(length = 500)
    private String error;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime finishedAt;

    private LocalDateTime expiresAt;
}
