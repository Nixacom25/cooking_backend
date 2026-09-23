package com.cooked.backend.service;

import com.cooked.backend.dto.request.NotificationCampaignRequest;
import com.cooked.backend.dto.response.NotificationCampaignResponse;
import com.cooked.backend.entity.NotificationCampaign;

import java.util.List;

public interface NotificationCampaignService {
    
    NotificationCampaignResponse createCampaign(NotificationCampaignRequest request);
    
    NotificationCampaignResponse getCampaign(Long id);
    
    List<NotificationCampaignResponse> getAllCampaigns();
    
    List<NotificationCampaignResponse> getCampaignsByStatus(NotificationCampaign.CampaignStatus status);
    
    NotificationCampaignResponse updateCampaign(Long id, NotificationCampaignRequest request);
    
    void deleteCampaign(Long id);
    
    void cancelCampaign(Long id);
    
    void sendCampaignNow(Long id);
    
    List<NotificationCampaignResponse> getCampaignsByCreator(String createdBy);
    
    // Scheduled task to process pending campaigns
    void processScheduledCampaigns();
    
    // Analytics
    NotificationCampaignResponse getCampaignAnalytics(Long id);
    
    // Tracking
    void trackNotificationOpen(Long campaignId);
    void trackNotificationClick(Long campaignId);
}