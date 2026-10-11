package com.cooked.backend.repository;

import com.cooked.backend.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Pending RevenueCat events, real-payment dedupe and backdated payments on H2. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RevenueCatReconciliationQueriesTest {

    @Autowired private TestEntityManager em;
    @Autowired private RevenueCatPendingEventRepository pending;
    @Autowired private SubscriptionPaymentRepository payments;
    @Autowired private UserRepository users;

    @Test
    void pendingMatchingDedupeAndBackdating() {
        pending.save(RevenueCatPendingEvent.builder().eventId("evt1").eventType("INITIAL_PURCHASE")
                .appUserIds(",$RCAnonymousID:abc,$RCAnonymousID:abc,").payload("{}").build());
        assertEquals(1, pending.findOpenFor("$RCAnonymousID:abc").size());
        assertEquals(0, pending.findOpenFor("$RCAnonymousID:ab").size());

        User u = em.persist(User.builder().email("emely@t.com").password("x").role(Role.CLIENT).status(Status.ACTIVE)
                .subscriptionStatus(SubscriptionStatus.ACTIVE).build());
        em.persist(User.builder().email("free@t.com").password("x").role(Role.CLIENT).status(Status.ACTIVE).subscriptionStatus(SubscriptionStatus.FREE).build());
        SubscriptionPayment p = new SubscriptionPayment();
        p.setUser(u); p.setAmount(new BigDecimal("29.99")); p.setStatus("SUCCESS"); p.setPlanType("YEARLY"); p.setStripePaymentId("rc_sync_1");
        p = payments.save(p);
        LocalDateTime purchase = LocalDateTime.of(2026, 10, 2, 5, 23);
        payments.setCreatedAt(p.getId(), purchase);
        em.flush();
        em.clear();

        assertEquals(purchase, payments.findById(p.getId()).orElseThrow().getCreatedAt());
        assertTrue(payments.existsRealPaymentBetween(u.getId(), purchase.minusHours(36), purchase.plusHours(36)));
        assertFalse(payments.existsRealPaymentBetween(u.getId(), purchase.plusDays(30), purchase.plusDays(31)));
        assertEquals(1, users.findPossibleSubscribers().size());
    }
}
