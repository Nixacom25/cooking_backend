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
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Intern screens: who validated each recipe, correction reasons share, intern threads. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InternScreensQueriesTest {

    @Autowired private TestEntityManager em;
    @Autowired private RecipeAssignmentHistoryRepository history;
    @Autowired private SupportTicketRepository tickets;

    @Test
    void validatorsAndReasons() {
        User admin = em.persist(User.builder().email("cheikh@test.com").firstname("Cheikh").lastname("Gueye").password("x").role(Role.ADMIN).status(Status.ACTIVE).build());
        User intern = em.persist(User.builder().email("amina@test.com").password("x").role(Role.EDITOR).status(Status.ACTIVE).build());
        Recipe r = em.persist(Recipe.builder().name("Couscous").build());
        RecipeAssignment a = em.persist(RecipeAssignment.builder().recipe(r).assignedByUser(admin).assignedToUser(intern).status(AssignmentStatus.VALIDATED).build());
        em.persist(RecipeAssignmentHistory.builder().recipeAssignment(a).actor(admin).action("NEEDS_CORRECTION")
                .previousStatus(AssignmentStatus.SUBMITTED_FOR_VALIDATION).newStatus(AssignmentStatus.NEEDS_CORRECTION)
                .errorCategories(List.of("MAUVAISE_IMAGE", "INGREDIENTS_INCORRECTS")).build());
        em.persist(RecipeAssignmentHistory.builder().recipeAssignment(a).actor(admin).action("NEEDS_CORRECTION")
                .previousStatus(AssignmentStatus.SUBMITTED_FOR_VALIDATION).newStatus(AssignmentStatus.NEEDS_CORRECTION)
                .errorCategories(List.of("MAUVAISE_IMAGE")).build());
        em.persist(RecipeAssignmentHistory.builder().recipeAssignment(a).actor(admin).action("VALIDATED")
                .previousStatus(AssignmentStatus.SUBMITTED_FOR_VALIDATION).newStatus(AssignmentStatus.VALIDATED).build());
        em.flush();

        var validators = history.findValidators(List.of(a.getId()));
        assertEquals(1, validators.size());
        assertEquals("Cheikh", validators.get(0).getFirstname());

        Map<String, Long> reasons = history.countCorrectionReasonsSince(LocalDateTime.now().minusDays(90)).stream()
                .collect(Collectors.toMap(RecipeAssignmentHistoryRepository.ReasonCount::getReason, RecipeAssignmentHistoryRepository.ReasonCount::getTotal));
        assertEquals(2L, reasons.get("MAUVAISE_IMAGE"));
        assertEquals(1L, reasons.get("INGREDIENTS_INCORRECTS"));
        assertEquals(0, history.countCorrectionReasonsSince(LocalDateTime.now().plusDays(1)).size());

        tickets.save(SupportTicket.builder().name("A").email("amina@test.com").subject("My workload").message("hi").source("INTERN").userId("u1").build());
        tickets.save(SupportTicket.builder().name("A").email("amina@test.com").subject("Web").message("hi").source("WEB").userId("u1").build());
        assertEquals(1, tickets.findAllBySourceAndUserIdOrderByCreatedAtDesc("INTERN", "u1").size());
    }
}
