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

/** Interns are only handed Explore/staff recipes: a client's or creator's own recipe never enters the pool. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AssignablePoolQueryTest {

    @Autowired private TestEntityManager em;
    @Autowired private RecipeRepository recipes;

    private User user(String email, Role role) {
        return em.persist(User.builder().email(email).password("x").role(role).status(Status.ACTIVE).build());
    }

    @Test
    void clientAndCreatorRecipesAreNotAssignable() {
        User client = user("client@test.com", Role.CLIENT);
        User creator = user("creator@test.com", Role.CREATOR);
        User admin = user("admin@test.com", Role.ADMIN);
        em.persist(Recipe.builder().name("Explore").origin(RecipeOrigin.EXPLORE).build());
        em.persist(Recipe.builder().name("By admin").origin(RecipeOrigin.EXPLORE).user(admin).build());
        em.persist(Recipe.builder().name("Client scan").origin(RecipeOrigin.SCAN).user(client).build());
        em.persist(Recipe.builder().name("Creator import").origin(RecipeOrigin.IMPORT).user(creator).build());
        em.flush();

        assertEquals(2, recipes.countUnassignedUnmodifiedRecipes());
        assertEquals(2, recipes.countUnmodifiedRecipes());
        assertEquals(List.of("By admin", "Explore"), recipes.findUnassignedUnmodifiedRecipes(PageRequest.of(0, 10))
                .stream().map(Recipe::getName).sorted().toList());
        assertEquals(0, recipes.countUnassignedUnmodifiedRecipesByOrigin(RecipeOrigin.SCAN));
    }
}
