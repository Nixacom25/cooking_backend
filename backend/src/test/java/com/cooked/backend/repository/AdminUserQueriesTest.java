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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Users table extras, team activity and alert read-state queries on H2. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AdminUserQueriesTest {

    @Autowired private TestEntityManager em;
    @Autowired private UserRepository users;
    @Autowired private AlertAckRepository acks;

    @Test
    void extrasActivityChurnAndAcks() {
        LocalDateTime now = LocalDateTime.now();
        User ana = em.persist(User.builder().email("ana@test.com").password("x").role(Role.CLIENT).status(Status.ACTIVE)
                .subscriptionStatus(SubscriptionStatus.EXPIRED).subscriptionExpiresAt(now.minusDays(3)).build());
        User ed = em.persist(User.builder().email("ed@test.com").password("x").role(Role.EDITOR).status(Status.ACTIVE).build());
        em.persist(DeviceSession.builder().user(ana).deviceName("Android App").token("a1").lastActive(now.minusDays(2)).build());
        em.persist(DeviceSession.builder().user(ana).deviceName("iOS App").token("a2").lastActive(now.minusHours(1)).build());
        em.persist(DeviceSession.builder().user(ed).deviceName("Linux PC").token("e1").lastActive(now.minusHours(5)).build());
        em.persist(payment(ana, "SUCCESS"));
        em.persist(payment(ana, "FAILED"));
        em.persist(AlertAck.builder().alertKey("ticket:1").ackBy("a@x.com").build());
        em.flush();

        assertEquals("iOS App", users.findSessionsOf(List.of(ana.getId())).get(0)[1]);
        assertEquals(0, new BigDecimal("9.99").compareTo((BigDecimal) users.sumPaymentsOf(List.of(ana.getId())).get(0)[1]));
        assertEquals(1, users.findLastSessionByRoles(List.of(Role.ADMIN, Role.EDITOR)).size());
        assertEquals(1, users.findAllByRoleIn(List.of(Role.EDITOR)).size());
        assertEquals(1, users.countLapsedSince(List.of(SubscriptionStatus.CANCELLED, SubscriptionStatus.EXPIRED), now.minusDays(30)));
        assertEquals(List.of("ticket:1"), acks.findExistingKeys(List.of("ticket:1", "ticket:2")));
    }

    private static SubscriptionPayment payment(User u, String status) {
        SubscriptionPayment p = new SubscriptionPayment();
        p.setUser(u);
        p.setAmount(new BigDecimal("9.99"));
        p.setStatus(status);
        p.setPlanType("MONTHLY");
        return p;
    }
}
