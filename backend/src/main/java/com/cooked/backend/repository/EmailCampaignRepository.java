package com.cooked.backend.repository;

import com.cooked.backend.entity.EmailCampaign;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Email campaigns and their audiences. A recipient is an active CLIENT account
 * with an email that hasn't unsubscribed; the audience filter is one fragment
 * shared by the counts and the paged reads.
 */
@Repository
public interface EmailCampaignRepository extends JpaRepository<EmailCampaign, UUID> {

    Page<EmailCampaign> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<EmailCampaign> findAllByStatusAndScheduledAtLessThanEqual(EmailCampaign.Status status, LocalDateTime at);

    String AUDIENCE = "u.role = com.cooked.backend.entity.Role.CLIENT and u.status = com.cooked.backend.entity.Status.ACTIVE "
            + "and u.email is not null and u.emailMarketingEnabled = true and ("
            + "(:aud = 'TRIAL' and u.subscriptionStatus = com.cooked.backend.entity.SubscriptionStatus.TRIAL "
            + "  and (u.subscriptionExpiresAt is null or u.subscriptionExpiresAt > :now)) "
            + "or (:aud = 'TRIAL_ENDING' and u.subscriptionStatus = com.cooked.backend.entity.SubscriptionStatus.TRIAL "
            + "  and u.subscriptionExpiresAt > :now and u.subscriptionExpiresAt <= :soon) "
            + "or (:aud = 'FREE' and (u.subscriptionStatus is null or u.subscriptionStatus = com.cooked.backend.entity.SubscriptionStatus.FREE) "
            + "  and not exists (select 1 from SubscriptionPayment p where p.user = u and upper(p.status) = 'SUCCESS')) "
            + "or (:aud = 'PAYING' and u.subscriptionStatus in (com.cooked.backend.entity.SubscriptionStatus.ACTIVE, com.cooked.backend.entity.SubscriptionStatus.PREMIUM) "
            + "  and (u.subscriptionExpiresAt is null or u.subscriptionExpiresAt > :now)) "
            + "or (:aud = 'INACTIVE_30' and u.lastActive < :inactiveBefore)) ";

    /** Fewer than 2 campaign emails in the last 7 days (frequency cap). */
    String UNDER_CAP = "(select count(s) from EmailCampaignSend s where s.userId = u.id and s.sentAt >= :weekAgo) < 2 ";

    @Query("select count(u) from User u where " + AUDIENCE)
    long countAudience(@Param("aud") String audience, @Param("now") LocalDateTime now, @Param("soon") LocalDateTime soon,
                       @Param("inactiveBefore") LocalDateTime inactiveBefore);

    @Query("select count(u) from User u where " + AUDIENCE + "and " + UNDER_CAP)
    long countAudienceUnderCap(@Param("aud") String audience, @Param("now") LocalDateTime now, @Param("soon") LocalDateTime soon,
                               @Param("inactiveBefore") LocalDateTime inactiveBefore, @Param("weekAgo") LocalDateTime weekAgo);

    /** Recipients still to email for a campaign: in the audience, under the cap, not already sent this campaign. */
    @Query("select u.id, u.email, u.firstname from User u where " + AUDIENCE + "and " + UNDER_CAP
            + "and not exists (select 1 from EmailCampaignSend x where x.campaignId = :campaignId and x.userId = u.id) order by u.id")
    List<Object[]> nextRecipients(@Param("aud") String audience, @Param("now") LocalDateTime now, @Param("soon") LocalDateTime soon,
                                  @Param("inactiveBefore") LocalDateTime inactiveBefore, @Param("weekAgo") LocalDateTime weekAgo,
                                  @Param("campaignId") UUID campaignId, Pageable page);
}
