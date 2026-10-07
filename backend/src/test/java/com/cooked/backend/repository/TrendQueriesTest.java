package com.cooked.backend.repository;

import com.cooked.backend.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Trend aggregates on a real (H2) database. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TrendQueriesTest {

    @Autowired private TestEntityManager em;
    @Autowired private TrendQueryRepository trends;

    private final LocalDateTime now = LocalDateTime.of(2026, 10, 6, 12, 0);
    private final LocalDateTime from = now.minusDays(7);
    private final LocalDateTime to = now.plusDays(1);

    @BeforeEach
    void setUp() {
        User client = em.persist(User.builder().email("c@test.com").password("x").role(Role.CLIENT).status(Status.ACTIVE).build());
        User admin = em.persist(User.builder().email("a@test.com").password("x").role(Role.ADMIN).status(Status.ACTIVE).build());
        Ingredient egg = em.persist(Ingredient.builder().name("Egg").build());
        RecipeCategory quick = em.persist(RecipeCategory.builder().name("Quick").type(CategoryType.values()[0]).build());

        Recipe scanned = em.persist(Recipe.builder().name("Shakshuka").origin(RecipeOrigin.SCAN).user(client).categories(Set.of(quick)).build());
        em.persist(RecipeIngredient.builder().recipe(scanned).ingredient(egg).quantity("2").build());
        Recipe old = em.persist(Recipe.builder().name("shakshuka").origin(RecipeOrigin.SCAN).user(client).build());
        em.persist(RecipeIngredient.builder().recipe(old).ingredient(egg).quantity("1").build());
        em.persist(Recipe.builder().name("Admin dish").origin(RecipeOrigin.MANUAL).user(admin).build());
        em.flush();
        setCreatedAt("Recipe", scanned.getId(), now.minusDays(1));
        setCreatedAt("Recipe", old.getId(), now.minusDays(10));

        ProductEvent s1 = em.persist(ProductEvent.builder().type(ProductEventType.WEB_SEARCH).success(true).detail("suya").build());
        ProductEvent s2 = em.persist(ProductEvent.builder().type(ProductEventType.WEB_SEARCH).success(true).detail("suya").build());
        em.flush();
        setCreatedAt("ProductEvent", s1.getId(), now.minusDays(1));
        setCreatedAt("ProductEvent", s2.getId(), now.minusDays(9));
        em.flush();
        em.clear();
    }

    @Test
    void currentAndPreviousWindows() {
        var page = PageRequest.of(0, 10);
        assertEquals(1L, trends.ingredientCounts(RecipeOrigin.SCAN, Role.CLIENT, from, to, page).get(0).getTotal());
        assertEquals("egg", trends.ingredientCountsIn(RecipeOrigin.SCAN, List.of("egg"), Role.CLIENT, from.minusDays(7), from).get(0).getLabel());

        var names = trends.recipeNameCounts(Role.CLIENT, from, to, page);
        assertEquals(1, names.size());                                   // admin recipe excluded
        assertEquals("shakshuka", names.get(0).getLabel());
        assertEquals(1L, trends.recipeNameCountsIn(List.of("shakshuka"), Role.CLIENT, from.minusDays(7), from).get(0).getTotal());

        assertEquals("Quick", trends.categoryCounts(Role.CLIENT, from, to).get(0).getLabel());

        assertEquals(1L, trends.eventDetailCounts(ProductEventType.WEB_SEARCH, from, to, page).get(0).getTotal());
        assertEquals(1L, trends.eventDetailCountsIn(ProductEventType.WEB_SEARCH, List.of("suya"), from.minusDays(7), from).get(0).getTotal());
        assertEquals(LocalDate.of(2026, 10, 5), trends.eventDetailDaily(ProductEventType.WEB_SEARCH, "suya", from, to).get(0).getDay());
    }

    private void setCreatedAt(String entity, Object id, LocalDateTime at) {
        em.getEntityManager().createQuery("update " + entity + " x set x.createdAt = :at where x.id = :id")
                .setParameter("at", at).setParameter("id", id).executeUpdate();
    }
}
