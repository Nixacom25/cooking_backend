package com.cooked.backend.repository;

import com.cooked.backend.dto.request.AdminRecipeFilter;
import com.cooked.backend.entity.*;
import com.cooked.backend.repository.spec.AdminRecipeSpecs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Feature usage, creator reach, push opt-outs and the "reported" recipe filter on H2. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FeatureUsageQueriesTest {

    @Autowired private TestEntityManager em;
    @Autowired private UserRepository users;
    @Autowired private CreatorStatsRepository creators;
    @Autowired private RecipeRepository recipes;

    @Test
    void queries() {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        User chef = em.persist(User.builder().email("chef@test.com").password("x").role(Role.CREATOR).status(Status.ACTIVE).build());
        User fan = User.builder().email("fan@test.com").password("x").role(Role.CLIENT).status(Status.ACTIVE).build();
        fan.setPushEnabled(false);
        em.persist(fan);
        Recipe mafe = em.persist(Recipe.builder().name("Mafe").user(chef).isPublic(true).build());
        Recipe yassa = em.persist(Recipe.builder().name("Yassa").user(chef).isPublic(true).build());
        em.persist(Cookbook.builder().name("Faves").user(fan).recipes(new HashSet<>(Set.of(mafe))).build());
        em.persist(Cookbook.builder().name("Mine").user(chef).recipes(new HashSet<>(Set.of(mafe))).build());
        Ingredient onion = em.persist(Ingredient.builder().name("Onion").build());
        em.persist(GroceryItem.builder().user(fan).ingredient(onion).recipe(yassa).quantity("2").build());
        em.persist(MealPlan.builder().user(fan).recipe(yassa).plannedDate(LocalDate.now()).mealType(MealType.DINNER).build());
        em.persist(ProductEvent.builder().type(ProductEventType.RECIPE_VIEW).success(true).detail(mafe.getId().toString()).userId(fan.getId()).build());
        em.persist(RecipeFlag.builder().recipeId(yassa.getId()).reason("BAD_IMAGE").build());
        em.flush();

        assertEquals(1, users.countGroceryUsersSince(since));
        assertEquals(2, users.countCookbookUsersSince(since));
        assertEquals(1, users.countMealPlanUsersSince(since));
        assertEquals(0, users.countGroceryUsersSinceAmong(since, List.of(chef.getId())));
        assertEquals(1, users.countPushDisabled());
        assertEquals(1, creators.countSavers(chef.getId()));
        assertEquals(1, creators.countViewsSince(chef.getId(), since));
        assertEquals(1, creators.countAllCreatorViewsSince(since));
        var reported = recipes.findAll(AdminRecipeSpecs.of(new AdminRecipeFilter(null, null, null, null, null, true)), PageRequest.of(0, 10));
        assertEquals(List.of("Yassa"), reported.map(Recipe::getName).getContent());
    }
}
