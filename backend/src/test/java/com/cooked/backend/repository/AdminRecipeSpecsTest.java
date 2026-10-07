package com.cooked.backend.repository;

import com.cooked.backend.dto.request.AdminRecipeFilter;
import com.cooked.backend.entity.*;
import com.cooked.backend.repository.spec.AdminRecipeSpecs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Admin Recipes filters on a real (H2) database. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AdminRecipeSpecsTest {

    @Autowired private TestEntityManager em;
    @Autowired private RecipeRepository recipes;
    private UUID italian;

    @BeforeEach
    void setUp() {
        RecipeCategory it = em.persist(RecipeCategory.builder().name("Italian").type(CategoryType.CUISINE).build());
        italian = it.getId();
        em.persist(Recipe.builder().name("Pasta").origin(RecipeOrigin.EXPLORE).cuisine(it).isPublic(true).image("https://x/p.jpg").build());
        em.persist(Recipe.builder().name("Pizza").origin(RecipeOrigin.IMPORT).cuisine(it).isPublic(false).image(" ").build());
        em.persist(Recipe.builder().name("Thieb").origin(RecipeOrigin.IMPORT).isDeleted(true).build());
        em.flush();
        em.clear();
    }

    private List<String> names(AdminRecipeFilter f) {
        return recipes.findAll(AdminRecipeSpecs.of(f), PageRequest.of(0, 20)).map(Recipe::getName).stream().sorted().toList();
    }

    @Test
    void filters() {
        assertEquals(List.of("Pasta", "Pizza"), names(new AdminRecipeFilter(null, null, italian, null, null)));
        assertEquals(List.of("Pasta"), names(new AdminRecipeFilter(null, null, null, "public", null)));
        assertEquals(List.of("Pizza"), names(new AdminRecipeFilter(null, null, null, "PRIVATE", null)));
        assertEquals(List.of("Thieb"), names(new AdminRecipeFilter(null, null, null, "DELETED", null)));
        assertEquals(List.of("Pasta"), names(new AdminRecipeFilter(null, null, null, null, true)));
        assertEquals(List.of("Pizza", "Thieb"), names(new AdminRecipeFilter(null, null, null, null, false)));
        assertEquals(List.of("Pizza"), names(new AdminRecipeFilter(RecipeOrigin.IMPORT, "piz", null, null, null)));
        assertEquals(2, recipes.findAll(AdminRecipeSpecs.of(new AdminRecipeFilter(RecipeOrigin.IMPORT, null, null, null, null)), PageRequest.of(0, 1)).getTotalElements());
    }
}
