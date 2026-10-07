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

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CreatorStatsQueriesTest {

    @Autowired private TestEntityManager em;
    @Autowired private CreatorStatsRepository stats;

    @Test
    void publicRecipesAndUses() {
        User c = em.persist(User.builder().email("chef@x.com").password("x").role(Role.CREATOR).status(Status.ACTIVE).build());
        User fan = em.persist(User.builder().email("fan@x.com").password("x").role(Role.CLIENT).status(Status.ACTIVE).build());
        Recipe hit = em.persist(Recipe.builder().name("Jollof").user(c).isPublic(true).build());
        em.persist(Recipe.builder().name("Draft").user(c).isPublic(false).build());
        em.persist(Recipe.builder().name("Other").user(c).isPublic(true).build());
        Ingredient rice = em.persist(Ingredient.builder().name("Rice").build());
        em.persist(GroceryItem.builder().user(fan).recipe(hit).ingredient(rice).quantity("1").build());
        em.flush();

        assertEquals(2, stats.countPublic(c.getId()));
        assertEquals(2, stats.countPublicBetween(c.getId(), LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1)));
        assertEquals(1, stats.totalUses(c.getId()));
        var top = stats.topRecipes(c.getId(), PageRequest.of(0, 5));
        assertEquals("Jollof", top.get(0).getName());
        assertEquals(1L, top.get(0).getUses());
        assertEquals(1, stats.publishedDaily(c.getId(), LocalDateTime.now().minusDays(2)).size());
    }
}
