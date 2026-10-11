package com.cooked.backend.repository;

import com.cooked.backend.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Campaign audiences, unsubscribes and the 2-per-week cap on H2. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EmailCampaignQueriesTest {

    @Autowired private TestEntityManager em;
    @Autowired private EmailCampaignRepository campaigns;

    private User user(String email, SubscriptionStatus st, LocalDateTime expires, LocalDateTime lastActive, boolean marketing) {
        User u = User.builder().email(email).password("x").role(Role.CLIENT).status(Status.ACTIVE)
                .subscriptionStatus(st).subscriptionExpiresAt(expires).emailMarketingEnabled(marketing).build();
        u.setLastActive(lastActive);
        return em.persist(u);
    }

    @Test
    void audiencesCapAndUnsubscribe() {
        LocalDateTime now = LocalDateTime.now();
        user("trial-long@t.com", SubscriptionStatus.TRIAL, now.plusDays(10), now, true);
        User ending = user("trial-ending@t.com", SubscriptionStatus.TRIAL, now.plusDays(2), now, true);
        user("trial-unsub@t.com", SubscriptionStatus.TRIAL, now.plusDays(2), now, false);
        user("free@t.com", SubscriptionStatus.FREE, null, now, true);
        User paidOnce = user("free-but-paid@t.com", SubscriptionStatus.FREE, null, now, true);
        user("paying@t.com", SubscriptionStatus.ACTIVE, now.plusDays(200), now, true);
        user("sleepy@t.com", SubscriptionStatus.FREE, null, now.minusDays(45), true);
        em.persist(User.builder().email("admin@t.com").password("x").role(Role.ADMIN).status(Status.ACTIVE).subscriptionStatus(SubscriptionStatus.TRIAL).subscriptionExpiresAt(now.plusDays(2)).build());
        SubscriptionPayment p = new SubscriptionPayment();
        p.setUser(paidOnce); p.setAmount(BigDecimal.ONE); p.setStatus("SUCCESS"); p.setPlanType("MONTHLY");
        em.persist(p);
        // "ending" already got 2 campaign emails this week.
        em.persist(EmailCampaignSend.builder().campaignId(UUID.randomUUID()).userId(ending.getId()).sentAt(now.minusDays(1)).build());
        em.persist(EmailCampaignSend.builder().campaignId(UUID.randomUUID()).userId(ending.getId()).sentAt(now.minusDays(2)).build());
        em.flush();

        LocalDateTime soon = now.plusDays(3), inactive = now.minusDays(30), weekAgo = now.minusDays(7);
        assertEquals(2, campaigns.countAudience("TRIAL", now, soon, inactive));
        assertEquals(1, campaigns.countAudience("TRIAL_ENDING", now, soon, inactive));
        assertEquals(0, campaigns.countAudienceUnderCap("TRIAL_ENDING", now, soon, inactive, weekAgo));
        assertEquals(2, campaigns.countAudience("FREE", now, soon, inactive)); // free + sleepy, not the one who paid
        assertEquals(1, campaigns.countAudience("PAYING", now, soon, inactive));
        assertEquals(1, campaigns.countAudience("INACTIVE_30", now, soon, inactive));

        UUID campaignId = UUID.randomUUID();
        var batch = campaigns.nextRecipients("TRIAL", now, soon, inactive, weekAgo, campaignId, PageRequest.of(0, 10));
        assertEquals(1, batch.size());
        assertEquals("trial-long@t.com", batch.get(0)[1]);
        em.persist(EmailCampaignSend.builder().campaignId(campaignId).userId((UUID) batch.get(0)[0]).sentAt(now).build());
        em.flush();
        assertEquals(0, campaigns.nextRecipients("TRIAL", now, soon, inactive, weekAgo, campaignId, PageRequest.of(0, 10)).size());
    }
}
