package com.cooked.backend.repository;

import com.cooked.backend.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Custom automation windows, conditions, once-per-user and the email cap on H2. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CustomAutomationQueriesTest {

    @Autowired private TestEntityManager em;
    @Autowired private CustomAutomationRepository automations;

    private User user(String email, SubscriptionStatus st, LocalDateTime expires, LocalDateTime lastActive, String token) {
        User u = User.builder().email(email).password("x").role(Role.CLIENT).status(Status.ACTIVE).subscriptionStatus(st).subscriptionExpiresAt(expires).build();
        u.setLastActive(lastActive);
        u.setFcmToken(token);
        return em.persist(u);
    }

    @Test
    void windowsConditionsAndOncePerUser() {
        LocalDateTime now = LocalDateTime.now();
        User soon = user("soon@t.com", SubscriptionStatus.TRIAL, now.plusHours(20), now.minusDays(1), "tok");
        user("later@t.com", SubscriptionStatus.TRIAL, now.plusDays(5), now.minusDays(1), null);
        user("asleep@t.com", SubscriptionStatus.TRIAL, now.plusHours(10), now.minusDays(20), null);
        User gone = user("gone@t.com", SubscriptionStatus.FREE, null, now.minusDays(16), "tok");
        em.flush();
        LocalDateTime recent = now.minusDays(7);

        assertEquals(2, automations.countWindow("TRIAL_ENDS_IN", now, now.plusDays(1)));
        assertEquals(1, automations.countPassing("TRIAL_ENDS_IN", now, now.plusDays(1), "TRIAL", "WITHIN_7_DAYS", recent));
        assertEquals(1, automations.countPushable("TRIAL_ENDS_IN", now, now.plusDays(1), "ANY", "ANY", recent));
        assertEquals(2, automations.countEmailable("TRIAL_ENDS_IN", now, now.plusDays(1), "ANY", "ANY", recent, recent));
        assertEquals(2, automations.countWindow("INACTIVE_DAYS", now.minusDays(21), now.minusDays(14))); // asleep (20 days) and gone (16 days)

        UUID aid = UUID.randomUUID();
        var first = automations.candidates("TRIAL_ENDS_IN", now, now.plusDays(1), "TRIAL", "ANY", recent, aid, LocalDateTime.of(2000, 1, 1, 0, 0), PageRequest.of(0, 10));
        assertEquals(2, first.size());
        em.persist(CustomAutomationFire.builder().automationId(aid).userId(soon.getId()).firedAt(now).build());
        em.flush();
        assertEquals(1, automations.candidates("TRIAL_ENDS_IN", now, now.plusDays(1), "TRIAL", "ANY", recent, aid, LocalDateTime.of(2000, 1, 1, 0, 0), PageRequest.of(0, 10)).size());

        assertEquals(1, automations.userPasses(gone.getId(), "FREE", "NOT_IN_7_DAYS", recent, aid, now.minusDays(7)));
        assertEquals(0, automations.userPasses(gone.getId(), "PAYING", "ANY", recent, aid, now.minusDays(7)));

        assertEquals(0, automations.importFailuresSince(now.minusDays(7)));
        assertEquals(0, automations.creatorApplicationsSince(now.minusDays(7)));
        assertEquals(0, automations.paymentFailureEmailsSince(now.minusDays(7)));
        assertEquals(0, automations.criticalErrorTimesSince(now.minusDays(7)).size());
    }
}
