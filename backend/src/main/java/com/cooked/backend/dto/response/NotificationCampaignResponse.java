package com.cooked.backend.dto.response;

import com.cooked.backend.entity.NotificationCampaign;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationCampaignResponse {
    
    private Long id;
    private String title;
    private String body;
    private String imageUrl;
    private String deepLink;
    private NotificationCampaign.TargetType targetType;
    private NotificationCampaign.UserSegment targetSegment;
    private LocalDateTime scheduledFor;
    private LocalDateTime sentAt;
    private NotificationCampaign.CampaignStatus status;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer totalRecipients;
    private Integer sentCount;
    private Integer failedCount;
    private Integer openedCount;
    private Integer clickedCount;
    
    // Calculated metrics
    private Double deliveryRate;
    private Double openRate;
    private Double clickRate;
}