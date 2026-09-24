package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.NotificationCampaignRequest;
import com.cooked.backend.dto.response.NotificationCampaignResponse;
import com.cooked.backend.entity.NotificationCampaign;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.NotificationCampaignRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.NotificationCampaignService;
import com.cooked.backend.service.PushNotificationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationCampaignServiceImpl implements NotificationCampaignService {
    
    private final NotificationCampaignRepository campaignRepository;
    private final UserRepository userRepository;
    private final PushNotificationService pushNotificationService;
    private final ObjectMapper objectMapper;
    
    @Override
    @Transactional
    public NotificationCampaignResponse createCampaign(NotificationCampaignRequest request) {
        NotificationCampaign campaign = NotificationCampaign.builder()
                .title(request.getTitle())
                .body(request.getBody())
                .imageUrl(request.getImageUrl())
                .deepLink(request.getDeepLink())
                .targetType(request.getTargetType())
                .targetSegment(request.getTargetSegment())
                .createdBy(request.getCreatedBy())
                .status(NotificationCampaign.CampaignStatus.DRAFT)
                .build();
        
        // Handle targeted users
        if (request.getTargetType() == NotificationCampaign.TargetType.TARGETED_USERS 
                && request.getTargetUserIds() != null) {
            try {
                campaign.setTargetUserIds(objectMapper.writeValueAsString(request.getTargetUserIds()));
            } catch (Exception e) {
                throw new BadRequestException("Failed to process target user IDs");
            }
        }
        
        // If scheduled for immediate send, set status to SENDING
        if (request.getScheduledFor() == null) {
            campaign.setStatus(NotificationCampaign.CampaignStatus.SENDING);
            campaign.setScheduledFor(LocalDateTime.now());
        } else {
            campaign.setStatus(NotificationCampaign.CampaignStatus.SCHEDULED);
            campaign.setScheduledFor(request.getScheduledFor());
        }
        
        NotificationCampaign saved = campaignRepository.save(campaign);
        
        // If immediate send, process it
        if (saved.getStatus() == NotificationCampaign.CampaignStatus.SENDING) {
            processCampaign(saved);
        }
        
        return mapToResponse(saved);
    }
    
    @Override
    public NotificationCampaignResponse getCampaign(Long id) {
        NotificationCampaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found"));
        return mapToResponse(campaign);
    }
    
    @Override
    public List<NotificationCampaignResponse> getAllCampaigns() {
        return campaignRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<NotificationCampaignResponse> getCampaignsByStatus(NotificationCampaign.CampaignStatus status) {
        return campaignRepository.findByStatusOrderByCreatedAtDesc(status).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional
    public NotificationCampaignResponse updateCampaign(Long id, NotificationCampaignRequest request) {
        NotificationCampaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found"));
        
        // Can only update DRAFT or SCHEDULED campaigns
        if (campaign.getStatus() != NotificationCampaign.CampaignStatus.DRAFT 
                && campaign.getStatus() != NotificationCampaign.CampaignStatus.SCHEDULED) {
            throw new BadRequestException("Can only update DRAFT or SCHEDULED campaigns");
        }
        
        campaign.setTitle(request.getTitle());
        campaign.setBody(request.getBody());
        campaign.setImageUrl(request.getImageUrl());
        campaign.setDeepLink(request.getDeepLink());
        campaign.setTargetType(request.getTargetType());
        campaign.setTargetSegment(request.getTargetSegment());
        
        if (request.getTargetType() == NotificationCampaign.TargetType.TARGETED_USERS 
                && request.getTargetUserIds() != null) {
            try {
                campaign.setTargetUserIds(objectMapper.writeValueAsString(request.getTargetUserIds()));
            } catch (Exception e) {
                throw new BadRequestException("Failed to process target user IDs");
            }
        }
        
        if (request.getScheduledFor() != null) {
            campaign.setScheduledFor(request.getScheduledFor());
            campaign.setStatus(NotificationCampaign.CampaignStatus.SCHEDULED);
        }
        
        NotificationCampaign saved = campaignRepository.save(campaign);
        return mapToResponse(saved);
    }
    
    @Override
    @Transactional
    public void deleteCampaign(Long id) {
        NotificationCampaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found"));
        
        // Can only delete DRAFT or SCHEDULED campaigns
        if (campaign.getStatus() != NotificationCampaign.CampaignStatus.DRAFT 
                && campaign.getStatus() != NotificationCampaign.CampaignStatus.SCHEDULED) {
            throw new BadRequestException("Can only delete DRAFT or SCHEDULED campaigns");
        }
        
        campaignRepository.delete(campaign);
    }
    
    @Override
    @Transactional
    public void cancelCampaign(Long id) {
        NotificationCampaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found"));
        
        // Can only cancel SCHEDULED campaigns
        if (campaign.getStatus() != NotificationCampaign.CampaignStatus.SCHEDULED) {
            throw new BadRequestException("Can only cancel SCHEDULED campaigns");
        }
        
        campaign.setStatus(NotificationCampaign.CampaignStatus.CANCELLED);
        campaignRepository.save(campaign);
    }
    
    @Override
    @Transactional
    public void sendCampaignNow(Long id) {
        NotificationCampaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found"));
        
        // Can only send DRAFT or SCHEDULED campaigns
        if (campaign.getStatus() != NotificationCampaign.CampaignStatus.DRAFT 
                && campaign.getStatus() != NotificationCampaign.CampaignStatus.SCHEDULED) {
            throw new BadRequestException("Can only send DRAFT or SCHEDULED campaigns");
        }
        
        campaign.setStatus(NotificationCampaign.CampaignStatus.SENDING);
        campaign.setScheduledFor(LocalDateTime.now());
        campaignRepository.save(campaign);
        
        processCampaign(campaign);
    }
    
    @Override
    public List<NotificationCampaignResponse> getCampaignsByCreator(String createdBy) {
        return campaignRepository.findByCreatedByOrderByCreatedAtDesc(createdBy).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    @Scheduled(fixedDelay = 60000) // Check every minute
    @Transactional
    public void processScheduledCampaigns() {
        List<NotificationCampaign> pendingCampaigns = campaignRepository.findPendingCampaigns();
        
        for (NotificationCampaign campaign : pendingCampaigns) {
            if (campaign.getStatus() == NotificationCampaign.CampaignStatus.SCHEDULED 
                    && campaign.getScheduledFor() != null 
                    && campaign.getScheduledFor().isBefore(LocalDateTime.now())) {
                campaign.setStatus(NotificationCampaign.CampaignStatus.SENDING);
                campaignRepository.save(campaign);
                processCampaign(campaign);
            }
        }
    }
    
    @Override
    public NotificationCampaignResponse getCampaignAnalytics(Long id) {
        NotificationCampaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found"));
        return mapToResponse(campaign);
    }
    
    @Override
    @Transactional
    public void trackNotificationOpen(Long campaignId) {
        NotificationCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found"));
        
        campaign.setOpenedCount((campaign.getOpenedCount() != null ? campaign.getOpenedCount() : 0) + 1);
        campaignRepository.save(campaign);
    }
    
    @Override
    @Transactional
    public void trackNotificationClick(Long campaignId) {
        NotificationCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found"));
        
        campaign.setClickedCount((campaign.getClickedCount() != null ? campaign.getClickedCount() : 0) + 1);
        campaignRepository.save(campaign);
    }
    
    private void processCampaign(NotificationCampaign campaign) {
        try {
            List<User> targetUsers = getTargetUsers(campaign);
            campaign.setTotalRecipients(targetUsers.size());
            
            int sentCount = 0;
            int failedCount = 0;
            
            for (User user : targetUsers) {
                if (user.getFcmToken() != null && !user.getFcmToken().isBlank()
                        && user.isPushEnabled() && user.isPushNewsOffersEnabled()) {
                    try {
                        java.util.Map<String, String> data = new java.util.HashMap<>();
                        data.put("campaignId", campaign.getId().toString());
                        if (campaign.getDeepLink() != null) {
                            data.put("deepLink", campaign.getDeepLink());
                        }
                        
                        pushNotificationService.sendPush(
                            user.getFcmToken(),
                            campaign.getTitle(),
                            campaign.getBody(),
                            data
                        );
                        sentCount++;
                    } catch (Exception e) {
                        log.error("Failed to send notification to user {}: {}", user.getEmail(), e.getMessage());
                        failedCount++;
                    }
                } else {
                    failedCount++;
                }
            }
            
            campaign.setSentCount(sentCount);
            campaign.setFailedCount(failedCount);
            campaign.setSentAt(LocalDateTime.now());
            
            if (sentCount > 0) {
                campaign.setStatus(NotificationCampaign.CampaignStatus.SENT);
            } else {
                campaign.setStatus(NotificationCampaign.CampaignStatus.FAILED);
            }
            
            campaignRepository.save(campaign);
            log.info("Campaign {} processed: {} sent, {} failed", campaign.getId(), sentCount, failedCount);
            
        } catch (Exception e) {
            log.error("Failed to process campaign {}: {}", campaign.getId(), e.getMessage());
            campaign.setStatus(NotificationCampaign.CampaignStatus.FAILED);
            campaignRepository.save(campaign);
        }
    }
    
    private List<User> getTargetUsers(NotificationCampaign campaign) {
        switch (campaign.getTargetType()) {
            case ALL_USERS:
                return userRepository.findAll();
                
            case TARGETED_USERS:
                if (campaign.getTargetUserIds() != null) {
                    try {
                        List<String> userEmails = objectMapper.readValue(
                            campaign.getTargetUserIds(), 
                            new TypeReference<List<String>>() {}
                        );
                        return userRepository.findByEmailIn(userEmails);
                    } catch (Exception e) {
                        log.error("Failed to parse target user IDs: {}", e.getMessage());
                        return new ArrayList<>();
                    }
                }
                return new ArrayList<>();
                
            case TARGETED_SEGMENT:
                return getUsersBySegment(campaign.getTargetSegment());
                
            case PREMIUM_USERS:
                // Creators, Admins, and Editors should not be included in PREMIUM_USERS segment
                // as they have special access, not actual paid subscriptions
                return userRepository.findBySubscriptionStatusNotNull().stream()
                    .filter(user -> user.getRole() != com.cooked.backend.entity.Role.CREATOR && 
                                 user.getRole() != com.cooked.backend.entity.Role.ADMIN && 
                                 user.getRole() != com.cooked.backend.entity.Role.EDITOR)
                    .collect(java.util.stream.Collectors.toList());
                
            case FREE_USERS:
                return userRepository.findBySubscriptionStatusNull();
                
            default:
                return new ArrayList<>();
        }
    }
    
    private List<User> getUsersBySegment(NotificationCampaign.UserSegment segment) {
        LocalDateTime now = LocalDateTime.now();
        
        switch (segment) {
            case ACTIVE_USERS:
                return userRepository.findByLastActiveAfter(now.minusDays(7));
            case INACTIVE_USERS:
                return userRepository.findByLastActiveBefore(now.minusDays(7));
            case NEW_USERS:
                return userRepository.findByCreatedAtAfter(now.minusDays(30));
            case TRIAL_USERS:
                return userRepository.findBySubscriptionStatus("TRIAL");
            case PAID_USERS:
                // Creators, Admins, and Editors should not be included in PAID_USERS segment
                // as they have special access, not actual paid subscriptions
                return userRepository.findBySubscriptionStatusNotNull().stream()
                    .filter(user -> user.getRole() != com.cooked.backend.entity.Role.CREATOR && 
                                 user.getRole() != com.cooked.backend.entity.Role.ADMIN && 
                                 user.getRole() != com.cooked.backend.entity.Role.EDITOR)
                    .collect(java.util.stream.Collectors.toList());
            case CHURNED_USERS:
                return userRepository.findBySubscriptionStatus("CANCELLED");
            default:
                return new ArrayList<>();
        }
    }
    
    private NotificationCampaignResponse mapToResponse(NotificationCampaign campaign) {
        NotificationCampaignResponse response = NotificationCampaignResponse.builder()
                .id(campaign.getId())
                .title(campaign.getTitle())
                .body(campaign.getBody())
                .imageUrl(campaign.getImageUrl())
                .deepLink(campaign.getDeepLink())
                .targetType(campaign.getTargetType())
                .targetSegment(campaign.getTargetSegment())
                .scheduledFor(campaign.getScheduledFor())
                .sentAt(campaign.getSentAt())
                .status(campaign.getStatus())
                .createdBy(campaign.getCreatedBy())
                .createdAt(campaign.getCreatedAt())
                .updatedAt(campaign.getUpdatedAt())
                .totalRecipients(campaign.getTotalRecipients())
                .sentCount(campaign.getSentCount())
                .failedCount(campaign.getFailedCount())
                .openedCount(campaign.getOpenedCount())
                .clickedCount(campaign.getClickedCount())
                .build();
        
        // Calculate metrics
        if (campaign.getTotalRecipients() != null && campaign.getTotalRecipients() > 0) {
            response.setDeliveryRate(
                (campaign.getSentCount() != null ? campaign.getSentCount() : 0) * 100.0 / campaign.getTotalRecipients()
            );
            response.setOpenRate(
                (campaign.getOpenedCount() != null ? campaign.getOpenedCount() : 0) * 100.0 / campaign.getTotalRecipients()
            );
            response.setClickRate(
                (campaign.getClickedCount() != null ? campaign.getClickedCount() : 0) * 100.0 / campaign.getTotalRecipients()
            );
        }
        
        return response;
    }
}