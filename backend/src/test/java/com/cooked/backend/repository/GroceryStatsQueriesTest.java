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

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Grocery analytics aggregates on H2. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GroceryStatsQueriesTest {

    @Autowired private TestEntityManager em;
    @Autowired private GroceryStatsRepository repo;

    @Test
    void aggregates() {
        User ana = em.persist(User.builder().email("ana@test.com").password("x").role(Role.CLIENT).status(Status.ACTIVE).build());
        User bob = em.persist(User.builder().email("bob@test.com").password("x").role(Role.CLIENT).status(Status.ACTIVE).build());
        Recipe mafe = em.persist(Recipe.builder().name("Mafe").user(ana).build());
        Ingredient onion = em.persist(Ingredient.builder().name("Onion").build());
        Ingredient rice = em.persist(Ingredient.builder().name("Rice").build());
        em.persist(GroceryItem.builder().user(ana).ingredient(onion).recipe(mafe).quantity("2").isBought(true).build());
        em.persist(GroceryItem.builder().user(ana).ingredient(rice).quantity("1 kg").build());
        em.persist(GroceryItem.builder().user(bob).ingredient(onion).recipe(mafe).quantity("1").build());
        em.flush();

        LocalDateTime from = LocalDateTime.now().minusDays(7), to = LocalDateTime.now().plusMinutes(1);
        assertEquals(3, repo.countAdded(from, to));
        assertEquals(2, repo.countUsers(from, to));
        assertEquals(1, repo.countBought(from, to));
        assertEquals(2, repo.countFromRecipes(from, to));
        assertEquals(2, repo.countUsersEver());
        assertEquals(1, repo.dailySince(from).size());
        assertEquals("Onion", repo.topIngredients(from, PageRequest.of(0, 5)).get(0).getLabel());
        assertEquals(2L, repo.topRecipes(from, PageRequest.of(0, 5)).get(0).getTotal());
    }
}
