package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "analytics_events")
public class AnalyticsEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String eventName; // paywall_view, paywall_click, etc.

    @Column(nullable = false)
    private String variantKey; // A or B

    private String userEmail;

    @CreationTimestamp
    private LocalDateTime timestamp;
    
    private String metadata; // JSON additionnel si besoin
}
