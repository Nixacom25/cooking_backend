package com.cooked.backend.repository;

import com.cooked.backend.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The Archive lists trashed recipes and recipes deleted by their owner, newest first; a reworked recipe leaves it. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ArchivedRecipesQueryTest {

    @Autowired private TestEntityManager em;
    @Autowired private RecipeRepository recipes;

    @Test
    void listsTrashAndOwnerDeletedRecipes() {
        em.persist(Recipe.builder().name("Live").origin(RecipeOrigin.EXPLORE).build());
        Recipe trashed = Recipe.builder().name("Trashed").origin(RecipeOrigin.EXPLORE).isDeleted(true)
                .deletedAt(LocalDateTime.now().minusDays(2)).build();
        em.persist(trashed);
        Recipe byOwner = Recipe.builder().name("By owner").origin(RecipeOrigin.EXPLORE).previousOrigin(RecipeOrigin.SCAN)
                .ownerDeletedAt(LocalDateTime.now()).ownerDeletedRole("CLIENT").build();
        em.persist(byOwner);
        em.flush();

        assertEquals(List.of("By owner", "Trashed"), recipes.findArchived().stream().map(Recipe::getName).toList());
        // Unmodified, not deleted: an owner-deleted recipe is in the pool interns get assigned.
        assertEquals(2, recipes.countUnassignedUnmodifiedRecipes());

        byOwner.setOwnerDeletedAt(null); // validated after rework
        em.flush();
        assertEquals(List.of("Trashed"), recipes.findArchived().stream().map(Recipe::getName).toList());
    }
}
