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

/** Intern leaderboard aggregates, all time and limited to a period, on H2. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LeaderboardQueriesTest {

    @Autowired private TestEntityManager em;
    @Autowired private RecipeAssignmentRepository assignments;

    @Test
    void countsByUserAndStatusWithAndWithoutPeriod() {
        LocalDateTime now = LocalDateTime.now();
        User admin = em.persist(User.builder().email("admin@test.com").password("x").role(Role.ADMIN).status(Status.ACTIVE).build());
        User intern = em.persist(User.builder().email("intern@test.com").password("x").role(Role.EDITOR).status(Status.ACTIVE).build());
        assign(admin, intern, AssignmentStatus.VALIDATED, now.minusDays(2));
        assign(admin, intern, AssignmentStatus.VALIDATED, now.minusDays(40));
        assign(admin, intern, AssignmentStatus.IN_PROGRESS, now.minusDays(1));
        em.clear();

        long all = assignments.countByUserAndStatus().stream().mapToLong(RecipeAssignmentRepository.UserStatusCount::getTotal).sum();
        var recent = assignments.countByUserAndStatusSince(now.minusDays(7));
        long recentValidated = recent.stream().filter(c -> c.getStatus() == AssignmentStatus.VALIDATED)
                .mapToLong(RecipeAssignmentRepository.UserStatusCount::getTotal).sum();

        assertEquals(3, all);
        assertEquals(2, recent.stream().mapToLong(RecipeAssignmentRepository.UserStatusCount::getTotal).sum());
        assertEquals(1, recentValidated);
    }

    @Autowired private RecipeRepository recipes;

    @Test
    void unassignedRecipesBySource() {
        User admin = em.persist(User.builder().email("a2@test.com").password("x").role(Role.ADMIN).status(Status.ACTIVE).build());
        User intern = em.persist(User.builder().email("i2@test.com").password("x").role(Role.EDITOR).status(Status.ACTIVE).build());
        em.persist(Recipe.builder().name("Free import").origin(RecipeOrigin.IMPORT).build());
        em.persist(Recipe.builder().name("Free explore").origin(RecipeOrigin.EXPLORE).build());
        Recipe taken = em.persist(Recipe.builder().name("Taken import").origin(RecipeOrigin.IMPORT).build());
        em.persist(RecipeAssignment.builder().recipe(taken).assignedByUser(admin).assignedToUser(intern).status(AssignmentStatus.ASSIGNED)
                .batchLabel("Chicken").priority("HIGH").build());
        em.flush();

        assertEquals(1, recipes.countUnassignedUnmodifiedRecipesByOrigin(RecipeOrigin.IMPORT));
        assertEquals("Free import", recipes.findUnassignedUnmodifiedRecipesByOrigin(RecipeOrigin.IMPORT, org.springframework.data.domain.PageRequest.of(0, 5)).get(0).getName());
        assertEquals(2, recipes.countUnassignedUnmodifiedRecipes());
    }

    @Test
    void movableAssignmentsNotStartedFirst() {
        User admin = em.persist(User.builder().email("a3@test.com").password("x").role(Role.ADMIN).status(Status.ACTIVE).build());
        User intern = em.persist(User.builder().email("i3@test.com").password("x").role(Role.EDITOR).status(Status.ACTIVE).build());
        assign(admin, intern, AssignmentStatus.IN_PROGRESS, LocalDateTime.now().minusDays(5));
        assign(admin, intern, AssignmentStatus.ASSIGNED, LocalDateTime.now().minusDays(1));
        assign(admin, intern, AssignmentStatus.VALIDATED, LocalDateTime.now().minusDays(9));
        em.flush();
        em.clear();
        var movable = assignments.findMovable(intern.getId(), List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.IN_PROGRESS), org.springframework.data.domain.PageRequest.of(0, 5));
        assertEquals(2, movable.size());
        assertEquals(AssignmentStatus.ASSIGNED, movable.get(0).getStatus());
    }

    private void assign(User by, User to, AssignmentStatus status, LocalDateTime at) {
        Recipe r = em.persist(Recipe.builder().name("R " + at).build());
        RecipeAssignment a = em.persist(RecipeAssignment.builder().recipe(r).assignedByUser(by).assignedToUser(to).status(status).build());
        em.flush();
        // assigned_date is a @CreationTimestamp: back-date it directly.
        em.getEntityManager().createQuery("update RecipeAssignment a set a.assignedDate = :at where a.id = :id")
                .setParameter("at", at).setParameter("id", a.getId()).executeUpdate();
    }
}
