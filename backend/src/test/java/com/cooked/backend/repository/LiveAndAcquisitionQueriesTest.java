package com.cooked.backend.repository;

import com.cooked.backend.dto.request.AdminUserFilter;
import com.cooked.backend.entity.*;
import com.cooked.backend.repository.spec.AdminUserSpecs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Live dashboard, acquisition and client-context queries on H2. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LiveAndAcquisitionQueriesTest {

    @Autowired private TestEntityManager em;
    @Autowired private UserRepository users;
    @Autowired private SiteVisitRepository visits;

    @Test
    void queries() {
        LocalDateTime now = LocalDateTime.now();
        User ana = User.builder().email("ana@test.com").password("x").role(Role.CLIENT).status(Status.ACTIVE).subscriptionStatus(SubscriptionStatus.TRIAL).build();
        ana.setAppVersion("1.0.5+107");
        ana.setCountry("SN");
        em.persist(ana);
        User bob = em.persist(User.builder().email("bob@test.com").password("x").role(Role.CLIENT).status(Status.ACTIVE).build());
        Recipe r = em.persist(Recipe.builder().name("Mafe").user(ana).build());
        Ingredient peanut = em.persist(Ingredient.builder().name("Peanut").build());
        em.persist(GroceryItem.builder().user(ana).ingredient(peanut).recipe(r).quantity("1").build());
        em.persist(MealPlan.builder().user(ana).recipe(r).plannedDate(LocalDate.now()).mealType(MealType.DINNER).build());
        pay(bob, now.minusMinutes(20));                       // new payer this hour
        pay(ana, now.minusDays(40));
        pay(ana, now.minusMinutes(5));                        // renewal, not new
        em.persist(SiteVisit.builder().day(LocalDate.now()).visitor("v1").path("/blog/a").referrer("google.com").build());
        em.persist(SiteVisit.builder().day(LocalDate.now()).visitor("v1").path("/").build());
        em.persist(SiteVisit.builder().day(LocalDate.now()).visitor("v2").path("/").build());
        em.flush();

        LocalDateTime midnight = LocalDate.now().atStartOfDay();
        assertEquals(1, users.countGroceryAddsBetween(midnight, now.plusMinutes(1)));
        assertEquals(1, users.countMealPlansBetween(midnight, now.plusMinutes(1)));
        assertEquals(1, users.countNewPayersSince(now.minusHours(1)));
        assertEquals(1, users.countFirstPaymentsBetween(now.minusDays(7), now.plusMinutes(1)));
        assertEquals(1, users.countSignupsWithStatus(Role.CLIENT, now.minusDays(1), SubscriptionStatus.TRIAL));
        assertEquals(2, visits.countVisitors(LocalDate.now(), LocalDate.now().plusDays(1)));
        assertEquals(1, visits.countBlogVisitors(LocalDate.now(), LocalDate.now().plusDays(1)));
        assertEquals("direct", visits.visitorsByReferrer(LocalDate.now(), LocalDate.now().plusDays(1)).get(0).getLabel());
        assertEquals(List.of("1.0.5+107"), users.findDistinctAppVersions());
        assertEquals(List.of("SN"), users.findDistinctCountries());
        assertEquals(1, users.findAll(AdminUserSpecs.of(new AdminUserFilter(null, null, null, null, null, null, null, null, "1.0.5+107", "sn"), now)).size());
    }

    /** createdAt is a @CreationTimestamp: back-date it after the insert. */
    private void pay(User u, LocalDateTime at) {
        SubscriptionPayment p = new SubscriptionPayment();
        p.setUser(u);
        p.setAmount(new BigDecimal("9.99"));
        p.setStatus("SUCCESS");
        p.setPlanType("MONTHLY");
        em.persist(p);
        em.flush();
        em.getEntityManager().createQuery("update SubscriptionPayment p set p.createdAt = :at where p.id = :id")
                .setParameter("at", at).setParameter("id", p.getId()).executeUpdate();
    }
}
