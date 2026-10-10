package com.cooked.backend.repository;

import com.cooked.backend.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Payments screen: filters, status tabs and KPI totals on H2. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PaymentsScreenQueriesTest {

    @Autowired private TestEntityManager em;
    @Autowired private SubscriptionPaymentRepository payments;

    @Test
    void filtersTabsAndTotals() {
        User maya = em.persist(User.builder().email("maya@test.com").firstname("Maya").lastname("Thompson").password("x").role(Role.CLIENT).status(Status.ACTIVE).build());
        User diego = em.persist(User.builder().email("diego@test.com").firstname("Diego").password("x").role(Role.CLIENT).status(Status.ACTIVE).build());
        em.persist(pay(maya, "Apple", "YEARLY", "SUCCESS", "49.99", "rc_1"));
        em.persist(pay(maya, "Apple", "YEARLY", "DUPLICATE", "49.99", "rc_2"));
        em.persist(pay(diego, "Google", "MONTHLY", "SUCCESS", "6.99", "rc_3"));
        em.persist(pay(diego, "STRIPE", "YEARLY", "REFUNDED", "49.99", "cs_4"));
        em.persist(pay(diego, "Apple", "MONTHLY", "SANDBOX", "6.99", "rc_5"));
        em.flush();
        LocalDateTime from = LocalDateTime.now().minusDays(1), to = LocalDateTime.now().plusMinutes(1);
        PageRequest page = PageRequest.of(0, 20, Sort.by("createdAt").descending());

        assertEquals(5, payments.searchPayments(from, to, "ALL", "ALL", "", "ALL", page).getTotalElements());
        assertEquals(3, payments.searchPayments(from, to, "APPLE", "ALL", "", "ALL", page).getTotalElements());
        assertEquals(1, payments.searchPayments(from, to, "WEB", "ALL", "", "ALL", page).getTotalElements());
        assertEquals(2, payments.searchPayments(from, to, "ALL", "ALL", "", "SUCCESS", page).getTotalElements());
        assertEquals(2, payments.searchPayments(from, to, "ALL", "MONTHLY", "", "ALL", page).getTotalElements());
        assertEquals(2, payments.searchPayments(from, to, "ALL", "ALL", "%thompson%", "ALL", page).getTotalElements());
        assertEquals(1, payments.searchPayments(from, to, "ALL", "ALL", "%cs_4%", "ALL", page).getTotalElements());
        // Outside the period: nothing.
        assertEquals(0, payments.searchPayments(from.minusDays(9), from.minusDays(8), "ALL", "ALL", "", "ALL", page).getTotalElements());

        Map<String, SubscriptionPaymentRepository.StatusTotal> totals = payments.totalsByStatus(from, to, "ALL", "ALL", "").stream()
                .collect(Collectors.toMap(SubscriptionPaymentRepository.StatusTotal::getLabel, t -> t));
        assertEquals(2L, totals.get("SUCCESS").getCount());
        assertEquals(0, new BigDecimal("56.98").compareTo(totals.get("SUCCESS").getAmount()));
        assertEquals(1L, totals.get("REFUNDED").getCount());
        assertEquals(1L, totals.get("SANDBOX").getCount());
    }

    private static SubscriptionPayment pay(User u, String store, String plan, String status, String amount, String ref) {
        SubscriptionPayment p = new SubscriptionPayment();
        p.setUser(u);
        p.setStore(store);
        p.setPlanType(plan);
        p.setStatus(status);
        p.setAmount(new BigDecimal(amount));
        p.setStripePaymentId(ref);
        return p;
    }
}
