package com.cooked.backend.repository;

import com.cooked.backend.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Every recipe with an empty lastModifiedBy is either free, movable, or in the review loop — never lost. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UnmodifiedPoolQueriesTest {

    @Autowired private TestEntityManager em;
    @Autowired private RecipeRepository recipes;
    @Autowired private RecipeAssignmentRepository assignments;

    private User admin, awa, omar;

    private Recipe recipe(String name, String lastModifiedBy) {
        Recipe r = Recipe.builder().name(name).origin(RecipeOrigin.IMPORT).build();
        r.setLastModifiedBy(lastModifiedBy);
        return em.persist(r);
    }

    private void assign(Recipe r, User to, AssignmentStatus status) {
        em.persist(RecipeAssignment.builder().recipe(r).assignedByUser(admin).assignedToUser(to).status(status).build());
    }

    @Test
    void poolCoversEveryUnmodifiedRecipe() {
        admin = em.persist(User.builder().email("pa@test.com").password("x").role(Role.ADMIN).status(Status.ACTIVE).build());
        awa = em.persist(User.builder().email("pawa@test.com").password("x").role(Role.EDITOR).status(Status.ACTIVE).build());
        omar = em.persist(User.builder().email("pomar@test.com").password("x").role(Role.EDITOR).status(Status.ACTIVE).build());

        recipe("Never assigned", null);
        recipe("Blank author", "  ");
        assign(recipe("Validated but untouched", null), awa, AssignmentStatus.VALIDATED);
        assign(recipe("Removed earlier", null), awa, AssignmentStatus.UNASSIGNED);
        assign(recipe("Awa not started", null), awa, AssignmentStatus.ASSIGNED);
        assign(recipe("Awa in progress", null), awa, AssignmentStatus.IN_PROGRESS);
        assign(recipe("Omar holds it", null), omar, AssignmentStatus.ASSIGNED);
        assign(recipe("Submitted", null), awa, AssignmentStatus.SUBMITTED_FOR_VALIDATION);
        assign(recipe("To fix", null), awa, AssignmentStatus.NEEDS_CORRECTION);
        recipe("Edited", "Awa Diop");
        Recipe gone = recipe("Deleted", null);
        gone.setDeleted(true);
        em.flush();

        long unmodified = recipes.countUnmodifiedRecipes();
        long free = recipes.countUnassignedUnmodifiedRecipes();
        long movable = recipes.countReassignableUnmodifiedRecipes();

        assertEquals(9, unmodified);
        assertEquals(4, free); // never assigned, blank author, validated-untouched, removed earlier
        assertEquals(3, movable);
        assertEquals(10, recipes.countLiveRecipes());
        assertEquals(free, recipes.findUnassignedUnmodifiedRecipes(PageRequest.of(0, 50)).size());
        assertEquals(4, recipes.countUnassignedUnmodifiedRecipesByOrigin(RecipeOrigin.IMPORT));
        assertEquals(0, recipes.countReassignableUnmodifiedRecipesByOrigin(RecipeOrigin.EXPLORE));

        // Moving to Omar skips the recipe he already holds; Awa sees all three.
        List<RecipeAssignment> forOmar = assignments.findReassignableUnmodified(omar.getId(), PageRequest.of(0, 50));
        assertEquals(2, forOmar.size());
        assertEquals(3, assignments.findReassignableUnmodifiedByOrigin(java.util.UUID.randomUUID(), RecipeOrigin.IMPORT, PageRequest.of(0, 50)).size());
    }
}
