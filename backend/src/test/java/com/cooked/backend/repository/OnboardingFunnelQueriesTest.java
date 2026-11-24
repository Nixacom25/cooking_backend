package com.cooked.backend.repository;

import com.cooked.backend.dto.request.OnboardingStepRequest;
import com.cooked.backend.entity.OnboardingStepView;
import com.cooked.backend.service.impl.OnboardingFunnelServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Onboarding funnel aggregates on H2: cohort, furthest screen, skipped screens, median, platform. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OnboardingFunnelQueriesTest {

    @Autowired private TestEntityManager em;
    @Autowired private OnboardingStepViewRepository repo;

    private void view(String install, int step, String platform, LocalDateTime at) {
        OnboardingStepView v = em.persist(OnboardingStepView.builder().installId(install).step(step).screen("S" + step).platform(platform).build());
        em.flush();
        // created_at is set by Hibernate on insert: move it to the wanted time
        em.getEntityManager().createQuery("update OnboardingStepView v set v.createdAt = :at where v.id = :id")
                .setParameter("at", at).setParameter("id", v.getId()).executeUpdate();
    }

    @Test
    void funnel() {
        LocalDateTime t0 = LocalDate.now().atTime(10, 0);
        // a: every screen 0..29, 10 minutes
        for (int i = 0; i <= 29; i++) view("a", i, "ios", t0.plusSeconds(i * 20L + (i == 29 ? 600 - 580 : 0)));
        // b: Google sign-in skips 24 and 25, finishes in 20 minutes
        for (int i = 0; i <= 29; i++) if (i != 24 && i != 25) view("b", i, "android", t0.plusSeconds(i == 29 ? 1200 : i * 10L));
        // c: stops at screen 3
        for (int i = 0; i <= 3; i++) view("c", i, "ios", t0.plusSeconds(i));
        // d: started 40 days ago: outside the cohort
        view("d", 0, "ios", t0.minusDays(40));
        view("d", 1, "ios", t0.minusDays(40));
        em.clear();

        var service = new OnboardingFunnelServiceImpl(repo);
        var f = service.funnel(LocalDate.now().minusDays(29), LocalDate.now(), null);
        assertEquals(29, f.lastStep());
        assertEquals(30, f.steps().size());
        assertEquals(3, f.started());
        assertEquals(2, f.finished());
        assertEquals(3, f.steps().get(3).reached());
        assertEquals(1, f.steps().get(3).leftHere());
        assertEquals(2, f.steps().get(4).reached());
        assertEquals(1, f.steps().get(24).reached());              // b skipped it
        assertEquals(0, f.steps().get(24).leftHere());             // nobody stopped there
        assertEquals(2, f.steps().get(29).leftHere());             // finished = furthest is the last screen
        assertEquals("S5", f.steps().get(5).screen());
        assertNotNull(f.medianSecondsToFinish());
        assertEquals((600 + 1200) / 2, f.medianSecondsToFinish());

        var ios = service.funnel(LocalDate.now().minusDays(29), LocalDate.now(), "iOS");
        assertEquals("ios", ios.platform());
        assertEquals(2, ios.started());
        assertEquals(1, ios.finished());
        assertEquals(600L, ios.medianSecondsToFinish());
    }

    @Test
    void recordsOncePerInstallAndScreen() {
        var service = new OnboardingFunnelServiceImpl(repo);
        var r = new OnboardingStepRequest("install-12345", 2, "Frustrations");
        service.record(r, "iOS", "1.0.6+118");
        service.record(r, "iOS", "1.0.6+118");
        assertEquals(1, repo.count());
        var saved = repo.findAll().get(0);
        assertEquals("ios", saved.getPlatform());
        assertNotEquals("install-12345", saved.getInstallId());   // hashed
    }
}
