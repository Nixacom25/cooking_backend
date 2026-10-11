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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Export datasets: counts and paged projections on H2 (no password, no token). */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DataExportQueriesTest {

    @Autowired private TestEntityManager em;
    @Autowired private DataExportRepository exports;

    @Test
    void countsAndPages() {
        LocalDateTime from = LocalDateTime.now().minusDays(1), to = LocalDateTime.now().plusMinutes(1);
        User u = em.persist(User.builder().email("ana@test.com").firstname("Ana").password("secret-hash").role(Role.CLIENT).status(Status.ACTIVE).build());
        Ingredient garlic = em.persist(Ingredient.builder().name("garlic").build());
        Recipe r = em.persist(Recipe.builder().name("Pasta").steps(new java.util.ArrayList<>(List.of("Boil", "Mix"))).build());
        em.persist(RecipeIngredient.builder().recipe(r).ingredient(garlic).quantity("3 cloves").build());
        SubscriptionPayment p = new SubscriptionPayment();
        p.setUser(u); p.setAmount(new BigDecimal("29.99")); p.setStatus("SUCCESS"); p.setPlanType("YEARLY"); p.setStore("Apple");
        em.persist(p);
        em.persist(ActivityLog.builder().user(u).title("Payment status changed").message("x").build());
        em.flush();

        assertEquals(1, exports.countUsers(from, to));
        assertEquals(1, exports.countRecipes(from, to));
        assertEquals(1, exports.countPayments(from, to));
        assertEquals(1, exports.countAudit(from, to));
        assertEquals(0, exports.countProviderCosts(LocalDate.now().minusDays(1), LocalDate.now().plusDays(1)));

        Object[] user = exports.users(from, to, PageRequest.of(0, 10)).get(0);
        assertEquals("ana@test.com", user[1]);
        for (Object v : user) assertNotEquals("secret-hash", v);
        Object[] recipe = exports.recipes(from, to, PageRequest.of(0, 10)).get(0);
        assertEquals("Pasta", recipe[1]);
        assertEquals("3 cloves", exports.recipeIngredients(List.of(r.getId())).get(0)[1]);
        assertEquals(2, exports.recipeSteps(List.of(r.getId())).size());
        assertEquals("ana@test.com", exports.payments(from, to, PageRequest.of(0, 10)).get(0)[2]);
        assertEquals("Payment status changed", exports.audit(from, to, PageRequest.of(0, 10)).get(0)[2]);
        assertTrue(exports.users(from.minusDays(9), from.minusDays(8), PageRequest.of(0, 10)).isEmpty());
    }
}
