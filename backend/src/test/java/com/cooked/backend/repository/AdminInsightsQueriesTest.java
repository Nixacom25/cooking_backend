package com.cooked.backend.repository;

import com.cooked.backend.entity.*;
import com.cooked.backend.service.impl.AdminInsightsServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Table aggregates (channels, creators, recipes, articles, paying users per feature) on H2. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AdminInsightsQueriesTest {

    @Autowired private TestEntityManager em;
    @Autowired private AdminInsightsRepository repo;

    @Test
    void aggregates() {
        User chef = em.persist(User.builder().email("chef@test.com").password("x").role(Role.CREATOR).status(Status.ACTIVE).build());
        User ana = em.persist(User.builder().email("ana@test.com").password("x").role(Role.CLIENT).status(Status.ACTIVE)
                .discoverySource("TikTok").subscriptionStatus(SubscriptionStatus.ACTIVE).build());
        em.persist(User.builder().email("bo@test.com").password("x").role(Role.CLIENT).status(Status.ACTIVE)
                .discoverySource("tiktok ").subscriptionStatus(SubscriptionStatus.TRIAL).build());
        Recipe mafe = em.persist(Recipe.builder().name("Mafe").user(chef).isPublic(true).build());
        em.persist(Cookbook.builder().name("Faves").user(ana).recipes(new HashSet<>(Set.of(mafe))).build());
        em.persist(MealPlan.builder().user(ana).recipe(mafe).plannedDate(LocalDate.now()).mealType(MealType.LUNCH).build());
        em.persist(ProductEvent.builder().type(ProductEventType.RECIPE_VIEW).success(true).detail(mafe.getId().toString()).userId(ana.getId()).build());
        em.persist(ProductEvent.builder().type(ProductEventType.SCAN).success(true).userId(ana.getId()).build());
        em.persist(SiteVisit.builder().day(LocalDate.now()).visitor("v1").path("/blog/mafe").build());
        SubscriptionPayment p = new SubscriptionPayment();
        p.setUser(ana);
        p.setAmount(new BigDecimal("9.99"));
        p.setStatus("SUCCESS");
        p.setPlanType("MONTHLY");
        em.persist(p);
        em.flush();

        var service = new AdminInsightsServiceImpl(repo);
        var sources = service.sources(30);
        assertEquals(1, sources.size());                          // "TikTok" and "tiktok " merged
        assertEquals("TikTok", sources.get(0).source());
        assertEquals(2, sources.get(0).signups());
        assertEquals(1, sources.get(0).trials());
        assertEquals(1, sources.get(0).paid());
        assertEquals(9.99, sources.get(0).revenue());

        assertEquals(1L, service.creators(List.of(chef.getId())).get(chef.getId()).get("savers"));
        assertEquals(1L, service.creators(List.of(chef.getId())).get(chef.getId()).get("views30d"));
        var r = service.recipes(List.of(mafe.getId())).get(mafe.getId());
        assertEquals(1L, r.get("views30d"));
        assertEquals(1L, r.get("cookbooks"));
        assertEquals(1L, r.get("mealPlans"));
        assertEquals(1L, service.articleVisitors(28).get("/blog/mafe"));
        assertEquals(1L, service.payingUsersByFeature(30).get("SCAN"));
        assertEquals(1, repo.groceryAddsOf(ana.getId()) + 1);
    }
}
