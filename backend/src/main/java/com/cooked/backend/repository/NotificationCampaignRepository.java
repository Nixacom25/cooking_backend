package com.cooked.backend.repository;

import com.cooked.backend.entity.NotificationCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationCampaignRepository extends JpaRepository<NotificationCampaign, Long> {
    
    List<NotificationCampaign> findByStatusOrderByCreatedAtDesc(NotificationCampaign.CampaignStatus status);
    
    List<NotificationCampaign> findByScheduledForBeforeAndStatus(
        LocalDateTime before, 
        NotificationCampaign.CampaignStatus status
    );
    
    @Query("SELECT nc FROM NotificationCampaign nc WHERE nc.status IN ('SCHEDULED', 'SENDING') ORDER BY nc.scheduledFor ASC")
    List<NotificationCampaign> findPendingCampaigns();
    
    @Query("SELECT nc FROM NotificationCampaign nc WHERE nc.createdBy = :createdBy ORDER BY nc.createdAt DESC")
    List<NotificationCampaign> findByCreatedByOrderByCreatedAtDesc(String createdBy);
}