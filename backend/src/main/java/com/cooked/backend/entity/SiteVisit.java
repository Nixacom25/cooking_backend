package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** One page view of the public website (visitor = hashed random browser id, no IP stored). */
@Entity
@Table(name = "site_visits", indexes = @Index(name = "idx_site_visits_day", columnList = "day"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private LocalDate day;

    @Column(nullable = false, length = 64)
    private String visitor;

    @Column(nullable = false, length = 200)
    private String path;

    @Column(length = 120)
    private String referrer;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
