package com.cooked.backend.repository;

import com.cooked.backend.config.DatabaseMigrationRunner;
import com.cooked.backend.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Approved recipes: published to the app by the startup backfill, and listed for the photo export. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ApprovedRecipesPublishTest {

    @Autowired private TestEntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RecipeAssignmentRepository assignments;

    private User admin, awa;

    private Recipe recipe(String name, String image) {
        return em.persist(Recipe.builder().name(name).origin(RecipeOrigin.EXPLORE).image(image).build());
    }

    private void assign(Recipe r, AssignmentStatus status) {
        em.persist(RecipeAssignment.builder().recipe(r).assignedByUser(admin).assignedToUser(awa).status(status).build());
    }

    private boolean live(Recipe r) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select status and is_public from recipes where id = ?", Boolean.class, r.getId()));
    }

    @Test
    void publishesApprovedRecipesAndListsTheirPhotos() {
        admin = em.persist(User.builder().email("ap-admin@test.com").password("x").role(Role.ADMIN).status(Status.ACTIVE).build());
        awa = em.persist(User.builder().email("ap-awa@test.com").password("x").role(Role.EDITOR).status(Status.ACTIVE).build());
        Recipe validated = recipe("Validated", "https://img.test/a.jpg");
        assign(validated, AssignmentStatus.VALIDATED);
        Recipe completed = recipe("Completed", null);
        assign(completed, AssignmentStatus.COMPLETED);
        Recipe submitted = recipe("Submitted", "https://img.test/b.jpg");
        assign(submitted, AssignmentStatus.SUBMITTED_FOR_VALIDATION);
        Recipe gone = recipe("Deleted", "https://img.test/c.jpg");
        gone.setDeleted(true);
        assign(gone, AssignmentStatus.VALIDATED);
        em.flush();

        DatabaseMigrationRunner runner = new DatabaseMigrationRunner(jdbc);
        assertEquals(2, runner.publishApprovedRecipes());
        assertTrue(live(validated));
        assertTrue(live(completed));
        assertFalse(live(submitted));
        assertFalse(live(gone));
        assertEquals(0, runner.publishApprovedRecipes());

        Set<String> names = assignments.findApprovedRecipeImages().stream()
                .map(RecipeAssignmentRepository.ApprovedRecipeImage::getName).collect(Collectors.toSet());
        assertEquals(Set.of("Validated"), names);
    }
}
