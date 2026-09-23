package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "notification_campaigns")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationCampaign {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String title;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;
    
    @Column(columnDefinition = "TEXT")
    private String imageUrl;
    
    // Deep link to redirect user when notification is tapped
    @Column(name = "deep_link")
    private String deepLink;
    
    // Campaign targeting
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TargetType targetType;
    
    // JSON array of user IDs for TARGETED_USERS type
    @Column(name = "target_user_ids", columnDefinition = "TEXT")
    private String targetUserIds;
    
    // User segments for TARGETED_SEGMENT type
    @Enumerated(EnumType.STRING)
    private UserSegment targetSegment;
    
    // Scheduling
    @Column(name = "scheduled_for")
    private LocalDateTime scheduledFor;
    
    @Column(name = "sent_at")
    private LocalDateTime sentAt;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CampaignStatus status;
    
    // Campaign metadata
    @Column(name = "created_by")
    private String createdBy;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "total_recipients")
    private Integer totalRecipients;
    
    @Column(name = "sent_count")
    @Builder.Default
    private Integer sentCount = 0;
    
    @Column(name = "failed_count")
    @Builder.Default
    private Integer failedCount = 0;
    
    @Column(name = "opened_count")
    @Builder.Default
    private Integer openedCount = 0;
    
    @Column(name = "clicked_count")
    @Builder.Default
    private Integer clickedCount = 0;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public enum TargetType {
        ALL_USERS,
        TARGETED_USERS,
        TARGETED_SEGMENT,
        PREMIUM_USERS,
        FREE_USERS
    }
    
    public enum UserSegment {
        ACTIVE_USERS,      // Users who used app in last 7 days
        INACTIVE_USERS,    // Users who haven't used app in 7+ days
        NEW_USERS,         // Users registered in last 30 days
        TRIAL_USERS,       // Users on trial
        PAID_USERS,        // Users with active subscription
        CHURNED_USERS      // Users who cancelled subscription
    }
    
    public enum CampaignStatus {
        DRAFT,
        SCHEDULED,
        SENDING,
        SENT,
        FAILED,
        CANCELLED
    }
}