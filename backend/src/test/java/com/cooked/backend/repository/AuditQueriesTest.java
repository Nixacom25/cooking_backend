package com.cooked.backend.repository;

import com.cooked.backend.dto.request.AdminAuditFilter;
import com.cooked.backend.entity.*;
import com.cooked.backend.repository.spec.AuditSpecs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Audit log filters and options on a real (H2) database. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuditQueriesTest {

    @Autowired private TestEntityManager em;
    @Autowired private ActivityLogRepository logs;
    private final LocalDateTime now = LocalDateTime.now();

    @BeforeEach
    void setUp() {
        User amina = em.persist(User.builder().email("amina@test.com").firstname("Amina").lastname("Sow").password("x").role(Role.EDITOR).status(Status.ACTIVE).build());
        User omar = em.persist(User.builder().email("omar@test.com").firstname("Omar").password("x").role(Role.EDITOR).status(Status.ACTIVE).build());
        User client = em.persist(User.builder().email("c@test.com").password("x").role(Role.CLIENT).status(Status.ACTIVE).build());
        log(amina, "Recipe updated", "Changed steps of Thieb", "RECIPE", 1);
        log(amina, "Ingredient updated", "Rice", "INGREDIENT", 20);
        log(omar, "Recipe updated", "Changed title of Mafe", "RECIPE", 2);
        log(client, "Recipe updated", "client action", "RECIPE", 1);
        em.flush();
        em.clear();
    }

    private void log(User u, String title, String message, String type, int daysAgo) {
        ActivityLog a = em.persist(ActivityLog.builder().user(u).title(title).message(message).entityType(type).build());
        em.flush();
        em.getEntityManager().createQuery("update ActivityLog a set a.createdAt = :at where a.id = :id")
                .setParameter("at", now.minusDays(daysAgo)).setParameter("id", a.getId()).executeUpdate();
    }

    private List<String> messages(AdminAuditFilter f) {
        return logs.findAll(AuditSpecs.of(Role.EDITOR, f, now), PageRequest.of(0, 20)).map(ActivityLog::getMessage).getContent();
    }

    @Test
    void filtersAndOrder() {
        assertEquals(List.of("Changed steps of Thieb", "Changed title of Mafe", "Rice"), messages(new AdminAuditFilter(null, null, null, null, null)));
        assertEquals(List.of("Changed steps of Thieb", "Rice"), messages(new AdminAuditFilter("AMINA@test.com", null, null, null, null)));
        assertEquals(List.of("Rice"), messages(new AdminAuditFilter(null, "ingredient", null, null, null)));
        assertEquals(List.of("Changed steps of Thieb", "Changed title of Mafe"), messages(new AdminAuditFilter(null, null, "Recipe updated", 7, null)));
        assertEquals(List.of("Changed title of Mafe"), messages(new AdminAuditFilter(null, null, null, null, "omar")));
    }

    @Test
    void options() {
        assertEquals(2, logs.findPeopleByRole(Role.EDITOR).size());
        assertEquals(List.of("INGREDIENT", "RECIPE"), logs.findAreasByRole(Role.EDITOR));
        assertEquals(List.of("Ingredient updated", "Recipe updated"), logs.findActionsByRole(Role.EDITOR));
    }
}
