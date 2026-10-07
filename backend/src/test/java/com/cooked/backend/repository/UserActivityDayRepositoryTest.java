package com.cooked.backend.repository;

import com.cooked.backend.entity.UserActivityDay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** DAU / WAU / MAU queries on a real (H2) database. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserActivityDayRepositoryTest {

    @Autowired private UserActivityDayRepository repo;

    @Test
    void countsPerDayAndDistinctUsers() {
        LocalDate d = LocalDate.of(2026, 10, 6);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        repo.save(UserActivityDay.builder().userId(a).day(d).build());
        repo.save(UserActivityDay.builder().userId(b).day(d).build());
        repo.save(UserActivityDay.builder().userId(a).day(d.minusDays(1)).build());
        repo.save(UserActivityDay.builder().userId(a).day(d.minusDays(20)).build());
        repo.flush();

        assertTrue(repo.existsByUserIdAndDay(a, d));
        assertFalse(repo.existsByUserIdAndDay(b, d.minusDays(1)));
        assertEquals(2, repo.countByDay(d.minusDays(6), d.plusDays(1)).size());
        assertEquals(2L, repo.countDistinctUsers(d.minusDays(6), d.plusDays(1)));
        assertEquals(1L, repo.countDistinctUsers(d.minusDays(29), d.minusDays(6)));
        assertEquals(d.minusDays(20), repo.firstDay());
        // restricted to a segment
        assertEquals(1L, repo.countDistinctUsersForUsers(d.minusDays(6), d.plusDays(1), java.util.List.of(b)));
        assertEquals(1, repo.countByDayForUsers(d.minusDays(6), d.plusDays(1), java.util.List.of(b)).size());
    }
}
