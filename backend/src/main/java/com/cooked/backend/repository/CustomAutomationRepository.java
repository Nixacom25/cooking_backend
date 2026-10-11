package com.cooked.backend.repository;

import com.cooked.backend.entity.CustomAutomation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Custom automations and who they match. Candidates are active CLIENT accounts;
 * the trigger window and the If-conditions are SQL, so a run never loads all users.
 */
@Repository
public interface CustomAutomationRepository extends JpaRepository<CustomAutomation, UUID> {

    List<CustomAutomation> findAllByOrderByCreatedAtDesc();

    List<CustomAutomation> findAllByStatusAndTrigger(CustomAutomation.Status status, CustomAutomation.Trigger trigger);

    List<CustomAutomation> findAllByStatus(CustomAutomation.Status status);

    /** trig: TRIAL_ENDS_IN (expiry in (now, until]), SIGNED_UP_DAYS_AGO / INACTIVE_DAYS (date in [from, until]). */
    String WINDOW = "u.role = com.cooked.backend.entity.Role.CLIENT and u.status = com.cooked.backend.entity.Status.ACTIVE and ("
            + "(:trig = 'TRIAL_ENDS_IN' and u.subscriptionStatus = com.cooked.backend.entity.SubscriptionStatus.TRIAL "
            + "  and u.subscriptionExpiresAt > :from and u.subscriptionExpiresAt <= :until) "
            + "or (:trig = 'SIGNED_UP_DAYS_AGO' and u.createdAt >= :from and u.createdAt <= :until) "
            + "or (:trig = 'INACTIVE_DAYS' and u.lastActive >= :from and u.lastActive <= :until)) ";

    String CONDITIONS = "(:aud = 'ANY' "
            + "or (:aud = 'TRIAL' and u.subscriptionStatus = com.cooked.backend.entity.SubscriptionStatus.TRIAL) "
            + "or (:aud = 'FREE' and (u.subscriptionStatus is null or u.subscriptionStatus in (com.cooked.backend.entity.SubscriptionStatus.FREE, "
            + "  com.cooked.backend.entity.SubscriptionStatus.EXPIRED, com.cooked.backend.entity.SubscriptionStatus.CANCELLED))) "
            + "or (:aud = 'PAYING' and u.subscriptionStatus in (com.cooked.backend.entity.SubscriptionStatus.ACTIVE, com.cooked.backend.entity.SubscriptionStatus.PREMIUM))) "
            + "and (:opened = 'ANY' or (:opened = 'WITHIN_7_DAYS' and u.lastActive >= :recent) "
            + "or (:opened = 'NOT_IN_7_DAYS' and (u.lastActive is null or u.lastActive < :recent))) ";

    /** Not fired for this user since [firedSince] (null-safe: pass an old date to mean "ever"). */
    String NOT_FIRED = "not exists (select 1 from CustomAutomationFire f where f.automationId = :aid and f.userId = u.id and f.firedAt >= :firedSince) ";

    @Query("select count(u) from User u where " + WINDOW)
    long countWindow(@Param("trig") String trigger, @Param("from") LocalDateTime from, @Param("until") LocalDateTime until);

    @Query("select count(u) from User u where " + WINDOW + "and " + CONDITIONS)
    long countPassing(@Param("trig") String trigger, @Param("from") LocalDateTime from, @Param("until") LocalDateTime until,
                      @Param("aud") String audience, @Param("opened") String opened, @Param("recent") LocalDateTime recent);

    @Query("select count(u) from User u where " + WINDOW + "and " + CONDITIONS
            + "and u.fcmToken is not null and u.pushEnabled = true and u.pushRemindersEnabled = true")
    long countPushable(@Param("trig") String trigger, @Param("from") LocalDateTime from, @Param("until") LocalDateTime until,
                       @Param("aud") String audience, @Param("opened") String opened, @Param("recent") LocalDateTime recent);

    @Query("select count(u) from User u where " + WINDOW + "and " + CONDITIONS + "and u.email is not null and u.emailMarketingEnabled = true "
            + "and (select count(s) from EmailCampaignSend s where s.userId = u.id and s.sentAt >= :weekAgo) < 2")
    long countEmailable(@Param("trig") String trigger, @Param("from") LocalDateTime from, @Param("until") LocalDateTime until,
                        @Param("aud") String audience, @Param("opened") String opened, @Param("recent") LocalDateTime recent,
                        @Param("weekAgo") LocalDateTime weekAgo);

    /** Users to act on now (time triggers). */
    @Query("select u from User u where " + WINDOW + "and " + CONDITIONS + "and " + NOT_FIRED + "order by u.id")
    List<com.cooked.backend.entity.User> candidates(@Param("trig") String trigger, @Param("from") LocalDateTime from, @Param("until") LocalDateTime until,
                                                    @Param("aud") String audience, @Param("opened") String opened, @Param("recent") LocalDateTime recent,
                                                    @Param("aid") UUID automationId, @Param("firedSince") LocalDateTime firedSince, Pageable page);

    /** One user against the If-conditions (event triggers). */
    @Query("select count(u) from User u where u.id = :uid and u.role = com.cooked.backend.entity.Role.CLIENT and " + CONDITIONS + "and " + NOT_FIRED)
    long userPasses(@Param("uid") UUID userId, @Param("aud") String audience, @Param("opened") String opened, @Param("recent") LocalDateTime recent,
                    @Param("aid") UUID automationId, @Param("firedSince") LocalDateTime firedSince);

    // --- event counts for dry runs ------------------------------------------------------------
    @Query("select count(distinct e.userId) from ProductEvent e where e.type = com.cooked.backend.entity.ProductEventType.IMPORT "
            + "and e.success = false and e.createdAt >= :since")
    long importFailuresSince(@Param("since") LocalDateTime since);

    @Query("select count(a) from CreatorApplication a where a.createdAt >= :since")
    long creatorApplicationsSince(@Param("since") LocalDateTime since);

    @Query("select count(e) from IntegrationEvent e where e.integration = com.cooked.backend.entity.IntegrationKey.BREVO "
            + "and e.name = 'PAYMENT_FAILED' and e.createdAt >= :since")
    long paymentFailureEmailsSince(@Param("since") LocalDateTime since);

    @Query("select e.createdAt from CriticalError e where e.createdAt >= :since order by e.createdAt")
    List<LocalDateTime> criticalErrorTimesSince(@Param("since") LocalDateTime since);
}
