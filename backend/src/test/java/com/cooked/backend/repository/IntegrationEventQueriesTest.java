package com.cooked.backend.repository;

import com.cooked.backend.entity.IntegrationEvent;
import com.cooked.backend.entity.IntegrationKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class IntegrationEventQueriesTest {

    @Autowired private IntegrationEventRepository repo;

    @Test
    void summariesByKeyAndName() {
        repo.save(IntegrationEvent.builder().integration(IntegrationKey.BREVO).name("WELCOME").success(true).httpStatus(201).build());
        repo.save(IntegrationEvent.builder().integration(IntegrationKey.BREVO).name("WELCOME").success(false).detail("HTTP 401").build());
        repo.save(IntegrationEvent.builder().integration(IntegrationKey.STRIPE).name("checkout.session.completed").success(true).build());
        repo.flush();
        LocalDateTime from = LocalDateTime.now().minusDays(1);

        var brevo = repo.summarySince(from).stream().filter(s -> s.getIntegration() == IntegrationKey.BREVO).findFirst().orElseThrow();
        assertEquals(2L, brevo.getTotal());
        assertEquals(1L, brevo.getFailures());
        assertEquals(1L, repo.byName(IntegrationKey.BREVO, from).get(0).getFailures());
        assertEquals(1, repo.daily(IntegrationKey.BREVO, from).size());
        assertNotNull(repo.firstAt(IntegrationKey.BREVO));
        assertEquals(2, repo.findByIntegrationOrderByCreatedAtDesc(IntegrationKey.BREVO, PageRequest.of(0, 10)).getTotalElements());
    }
}
