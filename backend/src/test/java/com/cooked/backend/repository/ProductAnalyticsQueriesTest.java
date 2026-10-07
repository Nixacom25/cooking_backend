package com.cooked.backend.repository;

import com.cooked.backend.entity.*;
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
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Runs the admin analytics aggregate queries on a real (H2) database. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductAnalyticsQueriesTest {

    @Autowired private TestEntityManager em;
    @Autowired private ProductEventRepository events;
    @Autowired private RecipeRepository recipes;
    @Autowired private UserRepository users;

    private final LocalDateTime now = LocalDateTime.of(2026, 10, 6, 12, 0);
    private User alice;

    @BeforeEach
    void setUp() {
        alice = em.persist(User.builder().email("alice@test.com").password("x").role(Role.CLIENT).status(Status.ACTIVE)
                .discoverySource("TIKTOK").build());
        User bob = em.persist(User.builder().email("bob@test.com").password("x").role(Role.CLIENT).status(Status.ACTIVE).build());
        em.persist(User.builder().email("admin@test.com").password("x").role(Role.ADMIN).status(Status.ACTIVE).build());
        setCreatedAt("User", alice.getId(), now.minusDays(1));
        setCreatedAt("User", bob.getId(), now.minusDays(40));

        event(ProductEventType.IMPORT, true, "allrecipes.com", 1, null, now.minusDays(1));
        event(ProductEventType.IMPORT, false, "allrecipes.com", null, "Blocked", now.minusDays(1));
        event(ProductEventType.IMPORT, true, "bbcgoodfood.com", 1, null, now.minusDays(2));
        event(ProductEventType.SCAN, true, "photo", 3, null, now.minusDays(1));
        event(ProductEventType.WEB_SEARCH, true, "pizza", 0, null, now.minusDays(1));
        event(ProductEventType.WEB_SEARCH, true, "pizza", 8, null, now.minusDays(1));

        Recipe r = em.persist(Recipe.builder().name("Scanned").origin(RecipeOrigin.SCAN).user(alice).build());
        setCreatedAt("Recipe", r.getId(), now.minusDays(3));
        em.flush();
    }

    @Test
    void summaryAndDaily() {
        LocalDateTime from = now.minusDays(30);
        Map<ProductEventType, ProductEventRepository.TypeSummary> byType = events.summaryByType(from).stream()
                .collect(Collectors.toMap(ProductEventRepository.TypeSummary::getType, s -> s));
        assertEquals(3L, byType.get(ProductEventType.IMPORT).getTotal());
        assertEquals(2L, byType.get(ProductEventType.IMPORT).getSuccesses());
        assertEquals(1L, byType.get(ProductEventType.IMPORT).getUsers());
        assertFalse(events.dailyCounts(from).isEmpty());
        assertNotNull(events.firstEventAt());
    }

    @Test
    void failedEventsPageWithUser() {
        var page = events.failures(ProductEventType.IMPORT, now.minusDays(30), PageRequest.of(0, 20));
        assertEquals(1, page.getTotalElements());
        var row = page.getContent().get(0);
        assertEquals("allrecipes.com", row.getDetail());
        assertEquals("Blocked", row.getReason());
        assertEquals("alice@test.com", row.getEmail());
        assertEquals(alice.getId(), row.getUserId());
        assertEquals(0, events.countByTypeAndSuccessFalseAndCreatedAtGreaterThanEqual(ProductEventType.IMPORT, now));
    }

    @Test
    void topDetailsZeroResultsAndReasons() {
        LocalDateTime from = now.minusDays(30);
        List<ProductEventRepository.LabelCount> sources = events.topDetails(ProductEventType.IMPORT, from, PageRequest.of(0, 10));
        assertEquals("allrecipes.com", sources.get(0).getLabel());
        assertEquals(2L, sources.get(0).getTotal());
        assertEquals(1L, sources.get(0).getFailures());

        List<ProductEventRepository.LabelCount> zero = events.zeroResultSearches(from, PageRequest.of(0, 10));
        assertEquals(1, zero.size());
        assertEquals(1L, zero.get(0).getTotal());

        assertEquals("Blocked", events.topFailureReasons(from, PageRequest.of(0, 10)).get(0).getReason());
    }

    @Test
    void recipeHistoryAndSignups() {
        LocalDateTime from = now.minusDays(30);
        var created = recipes.countCreatedByDayAndOrigin(from, List.of(RecipeOrigin.SCAN, RecipeOrigin.IMPORT));
        assertEquals(1, created.size());
        assertEquals(RecipeOrigin.SCAN, created.get(0).getOrigin());

        assertEquals(1, users.countSignupsByDay(Role.CLIENT, from).size());           // alice only (bob is 40 days old, admin excluded)
        assertEquals(1L, users.countSignupsBetween(Role.CLIENT, now.minusDays(60), from)); // bob
        assertEquals("TIKTOK", users.countSignupsBySource(Role.CLIENT, from).get(0).getLabel());
    }

    private void event(ProductEventType type, boolean ok, String detail, Integer results, String reason, LocalDateTime at) {
        ProductEvent e = em.persist(ProductEvent.builder().type(type).success(ok).detail(detail).resultCount(results)
                .failureReason(reason).durationMs(100).userId(alice.getId()).build());
        em.flush();
        setCreatedAt("ProductEvent", e.getId(), at);
    }

    private void setCreatedAt(String entity, Object id, LocalDateTime at) {
        em.flush();
        em.getEntityManager().createQuery("update " + entity + " x set x.createdAt = :at where x.id = :id")
                .setParameter("at", at).setParameter("id", id).executeUpdate();
    }
}
