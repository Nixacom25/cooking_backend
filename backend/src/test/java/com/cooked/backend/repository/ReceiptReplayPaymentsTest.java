package com.cooked.backend.repository;

import com.cooked.backend.config.DatabaseMigrationRunner;
import com.cooked.backend.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Receipt re-verification rows (iap_) logged on every app open are marked DUPLICATE, once. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReceiptReplayPaymentsTest {

    @Autowired private TestEntityManager em;
    @Autowired private JdbcTemplate jdbc;

    private SubscriptionPayment pay(User u, String ref) {
        SubscriptionPayment p = new SubscriptionPayment();
        p.setUser(u);
        p.setAmount(new BigDecimal("29.99"));
        p.setPlanType("YEARLY");
        p.setStatus("SUCCESS");
        p.setStripePaymentId(ref);
        p.setStore("Apple");
        return em.persist(p);
    }

    private List<String> statuses(User u) {
        return jdbc.queryForList("select status from subscription_payments where user_id = ? order by stripe_payment_id", String.class, u.getId());
    }

    @Test
    void marksRepeatedReceiptRowsOnce() {
        User glow = em.persist(User.builder().email("glow@test.com").password("x").role(Role.CLIENT).status(Status.ACTIVE).build());
        User rc = em.persist(User.builder().email("rc@test.com").password("x").role(Role.CLIENT).status(Status.ACTIVE).build());
        pay(glow, "iap_IOS_a");
        em.flush();
        pay(glow, "iap_IOS_b");
        pay(glow, "iap_IOS_c");
        pay(rc, "iap_IOS_x");
        pay(rc, "rc_evt_1");
        em.flush();

        DatabaseMigrationRunner runner = new DatabaseMigrationRunner(jdbc);
        assertEquals(3, runner.markReceiptReplayPayments());
        assertEquals(1, statuses(glow).stream().filter("SUCCESS"::equals).count());
        assertEquals(List.of("DUPLICATE", "SUCCESS"), statuses(rc)); // iap_IOS_x, rc_evt_1
        assertEquals(0, runner.markReceiptReplayPayments());
    }
}
