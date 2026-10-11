package com.cooked.backend.repository;

import com.cooked.backend.entity.EmailCampaignSend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface EmailCampaignSendRepository extends JpaRepository<EmailCampaignSend, UUID> {
    long countByCampaignId(UUID campaignId);

    long countByUserIdAndSentAtAfter(UUID userId, java.time.LocalDateTime after);
}
