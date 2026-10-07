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

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AmbassadorStatsQueriesTest {

    @Autowired private TestEntityManager em;
    @Autowired private AmbassadorStatsRepository stats;

    @Test
    void attributionAggregates() {
        Ambassador a = em.persist(Ambassador.builder().name("Grace").code("GRACE15").status(AmbassadorStatus.ACTIVE).commissionPercent(BigDecimal.TEN).build());
        LocalDateTime now = LocalDateTime.now();
        User u = User.builder().email("r@x.com").password("x").role(Role.CLIENT).status(Status.ACTIVE).build();
        u.setReferredByAmbassadorId(a.getId());
        u.setReferredAt(now.minusDays(2));
        u.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        em.persist(u);
        SubscriptionPayment before = new SubscriptionPayment();
        before.setUser(u); before.setAmount(new BigDecimal("5")); before.setStatus("SUCCESS"); before.setPlanType("MONTHLY");
        em.persist(before);
        SubscriptionPayment after = new SubscriptionPayment();
        after.setUser(u); after.setAmount(new BigDecimal("9.99")); after.setStatus("SUCCESS"); after.setPlanType("MONTHLY");
        em.persist(after);
        em.persist(AmbassadorClick.builder().ambassadorId(a.getId()).build());
        em.flush();
        em.getEntityManager().createQuery("update SubscriptionPayment p set p.createdAt = :at where p.id = :id")
                .setParameter("at", now.minusDays(3)).setParameter("id", before.getId()).executeUpdate();
        em.getEntityManager().createQuery("update SubscriptionPayment p set p.createdAt = :at where p.id = :id")
                .setParameter("at", now.minusDays(1)).setParameter("id", after.getId()).executeUpdate();
        em.clear();

        LocalDateTime from = now.minusDays(30), to = now.plusDays(1);
        assertEquals(1L, stats.referralsBetween(from, to).get(0).getTotal());
        assertEquals(1L, stats.referralsInStatus(List.of(SubscriptionStatus.ACTIVE), from, to).get(0).getTotal());
        var rev = stats.revenueBetween(from, to).get(0);
        assertEquals(0, new BigDecimal("9.99").compareTo(rev.getAmount()));                 // payment before the referral excluded
        assertEquals(1L, stats.clicksBetween(from, to).get(0).getTotal());
        assertEquals(1, stats.clicksDaily(a.getId(), from, to).size());
        assertEquals(1, stats.revenueByMonth(a.getId(), from).size());
    }
}
