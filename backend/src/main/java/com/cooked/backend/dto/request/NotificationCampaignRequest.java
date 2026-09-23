package com.cooked.backend.dto.request;

import com.cooked.backend.entity.NotificationCampaign;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class NotificationCampaignRequest {
    
    @NotBlank(message = "Title is required")
    private String title;
    
    @NotBlank(message = "Body is required")
    private String body;
    
    private String imageUrl;
    
    private String deepLink;
    
    @NotNull(message = "Target type is required")
    private NotificationCampaign.TargetType targetType;
    
    private List<String> targetUserIds; // For TARGETED_USERS type (email addresses for simplicity)
    
    private NotificationCampaign.UserSegment targetSegment; // For TARGETED_SEGMENT type
    
    private LocalDateTime scheduledFor; // null for immediate send
    
    private String createdBy; // Admin email or user ID
}