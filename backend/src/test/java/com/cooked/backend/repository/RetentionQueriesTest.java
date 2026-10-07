package com.cooked.backend.repository;

import com.cooked.backend.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Retention cohort queries on a real (H2) database. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RetentionQueriesTest {

    @Autowired private TestEntityManager em;
    @Autowired private UserRepository users;
    @Autowired private UserActivityDayRepository activity;
    @Autowired private ProductEventRepository events;

    @Test
    void cohortActivityDevicesAndFeatureUse() {
        User ana = em.persist(User.builder().email("ana@test.com").password("x").role(Role.CLIENT).status(Status.ACTIVE).discoverySource("TikTok").build());
        em.persist(User.builder().email("ed@test.com").password("x").role(Role.EDITOR).status(Status.ACTIVE).build());
        em.persist(DeviceSession.builder().user(ana).deviceName("iOS Device").token("t").lastActive(LocalDateTime.now()).build());
        em.persist(UserActivityDay.builder().userId(ana.getId()).day(LocalDate.now()).build());
        em.persist(ProductEvent.builder().type(ProductEventType.SCAN).success(true).userId(ana.getId()).build());
        em.flush();

        var cohort = users.findCohort(LocalDateTime.now().minusDays(1));
        assertEquals(1, cohort.size());
        assertEquals("TikTok", cohort.get(0).getDiscoverySource());
        assertEquals(1, activity.activitySince(LocalDate.now().minusDays(3)).size());
        assertEquals("iOS Device", users.findDeviceNames(List.of(ana.getId())).get(0)[1]);
        assertEquals(List.of(ana.getId()), events.usersWhoUsed(ProductEventType.SCAN, List.of(ana.getId())));
        assertEquals(List.of(), events.usersWhoUsed(ProductEventType.IMPORT, List.of(ana.getId())));
    }
}
