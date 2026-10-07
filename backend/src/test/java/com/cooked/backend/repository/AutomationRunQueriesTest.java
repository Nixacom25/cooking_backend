package com.cooked.backend.repository;

import com.cooked.backend.entity.AutomationRun;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AutomationRunQueriesTest {

    @Autowired private AutomationRunRepository repo;

    @Test
    void summaryAndLastRun() {
        repo.save(AutomationRun.builder().job("A.run").success(true).durationMs(100).build());
        repo.save(AutomationRun.builder().job("A.run").success(false).durationMs(300).detail("boom").build());
        repo.flush();
        var s = repo.summarySince(LocalDateTime.now().minusDays(1)).get(0);
        assertEquals(2L, s.getTotal());
        assertEquals(1L, s.getFailures());
        assertEquals(200.0, s.getAvgDurationMs());
        assertNotNull(repo.findFirstByJobOrderByCreatedAtDesc("A.run"));
    }
}
