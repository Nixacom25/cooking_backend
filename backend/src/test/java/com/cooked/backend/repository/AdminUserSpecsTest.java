package com.cooked.backend.repository;

import com.cooked.backend.dto.request.AdminUserFilter;
import com.cooked.backend.entity.*;
import com.cooked.backend.repository.spec.AdminUserSpecs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Admin Users filters on a real (H2) database. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AdminUserSpecsTest {

    @Autowired private TestEntityManager em;
    @Autowired private UserRepository users;

    private final LocalDateTime now = LocalDateTime.now();

    @BeforeEach
    void setUp() {
        User ios = em.persist(User.builder().email("ana@test.com").firstname("Ana").lastname("Diallo").password("x").role(Role.CLIENT)
                .status(Status.ACTIVE).subscriptionStatus(SubscriptionStatus.TRIAL).discoverySource("TikTok").build());
        User android = em.persist(User.builder().email("bob@test.com").password("x").role(Role.CLIENT)
                .status(Status.BLOCKED).subscriptionStatus(SubscriptionStatus.ACTIVE).discoverySource("Instagram ").build());
        em.persist(User.builder().email("creator@test.com").password("x").role(Role.CREATOR).status(Status.ACTIVE).build());
        em.persist(User.builder().email("amb@test.com").password("x").role(Role.CLIENT).status(Status.ACTIVE).build());
        em.persist(User.builder().email("admin@test.com").password("x").role(Role.ADMIN).status(Status.ACTIVE).build());
        em.persist(Ambassador.builder().name("Amb").email("AMB@test.com").code("AMB15").commissionPercent(new BigDecimal("20"))
                .status(AmbassadorStatus.ACTIVE).build());
        em.persist(DeviceSession.builder().user(ios).deviceName("iOS Device").token("t1").lastActive(now).build());
        em.persist(DeviceSession.builder().user(android).deviceName("Android Device").token("t2").lastActive(now).build());
        em.flush();
        em.getEntityManager().createQuery("update User u set u.createdAt = :at where u.email = 'bob@test.com'")
                .setParameter("at", now.minusDays(100)).executeUpdate();
        em.clear();
    }

    private List<String> emails(AdminUserFilter f) {
        return users.findAll(AdminUserSpecs.of(f, now)).stream().map(User::getEmail).sorted().toList();
    }

    private static AdminUserFilter none() {
        return new AdminUserFilter(null, null, null, null, null, null, null, null);
    }

    @Test
    void clientsOnlyByDefault() {
        assertEquals(List.of("amb@test.com", "ana@test.com", "bob@test.com"), emails(none()));
    }

    @Test
    void eachFilter() {
        assertEquals(List.of("ana@test.com"), emails(new AdminUserFilter("diallo", null, null, null, null, null, null, null)));
        assertEquals(List.of("ana@test.com"), emails(new AdminUserFilter(null, "IOS", null, null, null, null, null, null)));
        assertEquals(List.of("bob@test.com"), emails(new AdminUserFilter(null, "android", null, null, null, null, null, null)));
        assertEquals(List.of(), emails(new AdminUserFilter(null, "WEB", null, null, null, null, null, null)));
        assertEquals(List.of("bob@test.com"), emails(new AdminUserFilter(null, null, "active", null, null, null, null, null)));
        assertEquals(List.of(), emails(new AdminUserFilter(null, null, "NOPE", null, null, null, null, null)));
        assertEquals(List.of("amb@test.com", "ana@test.com"), emails(new AdminUserFilter(null, null, null, 30, null, null, null, null)));
        assertEquals(List.of("bob@test.com"), emails(new AdminUserFilter(null, null, null, null, "instagram", null, null, null)));
        assertEquals(List.of("bob@test.com"), emails(new AdminUserFilter(null, null, null, null, null, "BLOCKED", null, null)));
        assertEquals(List.of("ana@test.com"), emails(new AdminUserFilter(null, null, null, null, null, null, true, null)));
        assertEquals(List.of("amb@test.com", "creator@test.com"), emails(new AdminUserFilter(null, null, null, null, null, null, null, true)));
    }

    @Test
    void distinctSources() {
        assertEquals(List.of("Instagram", "TikTok"), users.findDistinctDiscoverySources());
    }
}
