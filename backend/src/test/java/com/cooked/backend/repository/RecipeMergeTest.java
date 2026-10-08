package com.cooked.backend.repository;

import com.cooked.backend.entity.*;
import com.cooked.backend.service.impl.RecipeModerationServiceImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Duplicate merge on a real (H2) database: cookbooks, meal plans and grocery items move, the duplicate is archived. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RecipeMergeTest {

    @Autowired private TestEntityManager tem;
    @Autowired private EntityManager em;
    @Autowired private RecipeRepository recipes;
    @Autowired private RecipeFlagRepository flags;

    @Test
    void mergeMovesReferencesAndArchivesTheDuplicate() {
        User u = tem.persist(User.builder().email("u@test.com").password("x").role(Role.CLIENT).status(Status.ACTIVE).build());
        Recipe keep = tem.persist(Recipe.builder().name("Thieboudienne").user(u).build());
        Recipe dup = tem.persist(Recipe.builder().name("Thieb").user(u).build());
        tem.persist(Cookbook.builder().name("Both").user(u).recipes(new HashSet<>(Set.of(keep, dup))).build());
        Cookbook onlyDup = tem.persist(Cookbook.builder().name("Dup only").user(u).recipes(new HashSet<>(Set.of(dup))).build());
        tem.persist(MealPlan.builder().user(u).recipe(dup).plannedDate(LocalDate.now()).mealType(MealType.LUNCH).build());
        Ingredient rice = tem.persist(Ingredient.builder().name("Rice").build());
        tem.persist(GroceryItem.builder().user(u).ingredient(rice).recipe(dup).quantity("1 kg").build());
        tem.flush();

        var service = new RecipeModerationServiceImpl(flags, recipes, em);
        var before = service.stats(dup.getId());
        assertEquals(2, before.cookbooks());
        assertEquals(1, before.mealPlans());
        assertEquals(1, before.groceryAdds());
        assertEquals(0, before.openReports());
        var result = service.merge(keep.getId(), dup.getId(), "boss@cooked.app");
        tem.flush();
        tem.clear();

        assertEquals(1, result.cookbooks());       // "Both" already had the kept recipe
        assertEquals(1, result.mealPlans());
        assertEquals(1, result.groceryItems());
        assertTrue(recipes.findById(dup.getId()).orElseThrow().isDeleted());
        Cookbook reloaded = tem.find(Cookbook.class, onlyDup.getId());
        assertEquals(Set.of(keep.getId()), reloaded.getRecipes().stream().map(Recipe::getId).collect(java.util.stream.Collectors.toSet()));
        assertEquals("DUPLICATE", flags.findByRecipeIdOrderByCreatedAtDesc(dup.getId()).get(0).getReason());
        assertThrows(com.cooked.backend.exception.BadRequestException.class, () -> service.merge(keep.getId(), keep.getId(), "x"));
    }
}
