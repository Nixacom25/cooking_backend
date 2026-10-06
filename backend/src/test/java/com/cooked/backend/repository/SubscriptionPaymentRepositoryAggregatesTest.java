package com.cooked.backend.repository;

import com.cooked.backend.entity.Role;
import com.cooked.backend.entity.Status;
import com.cooked.backend.entity.SubscriptionPayment;
import com.cooked.backend.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs the admin revenue aggregate queries on a real (H2) database, so the
 * HQL (date casts, extract, exists sub-query) is checked, not just mocked.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SubscriptionPaymentRepositoryAggregatesTest {

    @Autowired private TestEntityManager em;
    @Autowired private SubscriptionPaymentRepository repository;

    private final LocalDateTime now = LocalDateTime.of(2026, 10, 6, 12, 0);
    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        alice = em.persist(user("alice@test.com"));
        bob = em.persist(user("bob@test.com"));
        // alice: paid 50 days ago (before the window) and again 5 days ago -> 1 renewal
        pay(alice, "9.99", "MONTHLY", "SUCCESS", "Apple", now.minusDays(50));
        pay(alice, "9.99", "MONTHLY", "SUCCESS", "Apple", now.minusDays(5));
        // bob: first payment 2 days ago (yearly, Google) + one failed + one refund
        pay(bob, "29.99", "YEARLY", "SUCCESS", "Google", now.minusDays(2));
        pay(bob, "29.99", "YEARLY", "FAILED", "Google", now.minusDays(1));
        pay(bob, "29.99", "YEARLY", "REFUNDED", "Google", now.minusDays(1));
        em.flush();
    }

    @Test
    void sumsAndCounts() {
        LocalDateTime from30 = now.minusDays(30);
        assertEquals(0, new BigDecimal("39.98").compareTo(repository.sumSuccessBetween(from30, now)));
        assertEquals(0, new BigDecimal("9.99").compareTo(repository.sumSuccessBetween(now.minusDays(60), from30)));
        assertEquals(0, new BigDecimal("49.97").compareTo(repository.sumSuccess()));
        assertEquals(2, repository.countDistinctPayers());
        assertEquals(2, repository.countSuccessSince(from30));
        assertEquals(1, repository.countRenewalsSince(from30));
        assertEquals(1, repository.countByStatusKeywordSince("FAIL", from30));
        assertEquals(1, repository.countByStatusKeywordSince("REFUND", from30));
    }

    @Test
    void groupings() {
        LocalDateTime from30 = now.minusDays(30);
        Map<String, BigDecimal> byStore = repository.sumSuccessByStoreSince(from30).stream()
                .collect(Collectors.toMap(SubscriptionPaymentRepository.LabelAmount::getLabel, SubscriptionPaymentRepository.LabelAmount::getAmount));
        assertEquals(0, new BigDecimal("9.99").compareTo(byStore.get("Apple")));
        assertEquals(0, new BigDecimal("29.99").compareTo(byStore.get("Google")));

        assertEquals(2, repository.sumSuccessByPlanTypeSince(from30).size());

        Map<LocalDate, BigDecimal> byDay = repository.sumSuccessByDaySince(from30).stream()
                .collect(Collectors.toMap(SubscriptionPaymentRepository.DayAmount::getDay, SubscriptionPaymentRepository.DayAmount::getAmount));
        assertEquals(0, new BigDecimal("29.99").compareTo(byDay.get(now.minusDays(2).toLocalDate())));

        var months = repository.sumSuccessByMonthSince(now.minusMonths(3));
        assertEquals(2, months.size()); // August (50 days ago) and October
    }

    private User user(String email) {
        return User.builder().email(email).password("x").role(Role.CLIENT).status(Status.ACTIVE).build();
    }

    private void pay(User user, String amount, String plan, String status, String store, LocalDateTime at) {
        SubscriptionPayment p = new SubscriptionPayment();
        p.setUser(user);
        p.setAmount(new BigDecimal(amount));
        p.setPlanType(plan);
        p.setStatus(status);
        p.setStore(store);
        em.persist(p);
        em.flush();
        // createdAt is set by @CreationTimestamp on insert: move it to the wanted date.
        em.getEntityManager().createQuery("update SubscriptionPayment p set p.createdAt = :at where p.id = :id")
                .setParameter("at", at).setParameter("id", p.getId()).executeUpdate();
    }
}
