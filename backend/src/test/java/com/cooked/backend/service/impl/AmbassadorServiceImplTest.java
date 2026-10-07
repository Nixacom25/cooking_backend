package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.AmbassadorRequest;
import com.cooked.backend.entity.*;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.repository.*;
import com.cooked.backend.service.WorkspaceSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AmbassadorServiceImplTest {

    private final AmbassadorRepository repo = mock(AmbassadorRepository.class);
    private final AmbassadorClickRepository clicks = mock(AmbassadorClickRepository.class);
    private final AmbassadorPayoutRepository payouts = mock(AmbassadorPayoutRepository.class);
    private final AmbassadorStatsRepository stats = mock(AmbassadorStatsRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final WorkspaceSettingsService settings = mock(WorkspaceSettingsService.class);
    private AmbassadorServiceImpl service;

    @BeforeEach
    void setUp() {
        when(settings.current()).thenReturn(WorkspaceSettings.defaults());
        when(repo.save(any())).thenAnswer(i -> { Ambassador a = i.getArgument(0); if (a.getId() == null) a.setId(UUID.randomUUID()); return a; });
        service = new AmbassadorServiceImpl(repo, clicks, payouts, stats, users, settings, "https://x/r");
    }

    @Test
    void codesAreGeneratedUniqueAndDefaultCommissionApplies() {
        when(repo.existsByCodeIgnoreCase("GRACEOKAFO")).thenReturn(true);
        AmbassadorRequest r = new AmbassadorRequest();
        r.setName("Grace Okafor-Smith");
        var a = service.create(r);
        assertEquals("GRACEOKAFO2", a.getCode());
        assertEquals(0, BigDecimal.valueOf(20).compareTo(a.getCommissionPercent()));
        assertEquals("https://x/r/GRACEOKAFO2", a.getLink());
        assertEquals(AmbassadorStatus.ACTIVE, a.getStatus());
    }

    @Test
    void referralIsAttachedOnceAndOnlyForActiveCodes() {
        Ambassador grace = Ambassador.builder().id(UUID.randomUUID()).name("Grace").code("GRACE15").status(AmbassadorStatus.ACTIVE).commissionPercent(BigDecimal.TEN).build();
        Ambassador paused = Ambassador.builder().id(UUID.randomUUID()).name("Ben").code("BENO").status(AmbassadorStatus.PAUSED).commissionPercent(BigDecimal.TEN).build();
        when(repo.findByCodeIgnoreCase("grace15")).thenReturn(Optional.of(grace));
        when(repo.findByCodeIgnoreCase("BENO")).thenReturn(Optional.of(paused));
        User u = User.builder().email("u@x.com").build();

        assertEquals("Grace", service.attachReferral(u, " grace15 ").orElseThrow().getName());
        assertEquals(grace.getId(), u.getReferredByAmbassadorId());
        assertNotNull(u.getReferredAt());
        assertThrows(BadRequestException.class, () -> service.attachReferral(u, "grace15"));   // already applied
        assertTrue(service.attachReferral(User.builder().build(), "BENO").isEmpty());         // paused
        assertTrue(service.attachReferral(User.builder().build(), "<script>").isEmpty());
    }

    @Test
    void commissionAndPayoutRules() {
        assertEquals(5.99, AmbassadorServiceImpl.commission(new BigDecimal("29.95"), new BigDecimal("20")));
        Ambassador a = Ambassador.builder().id(UUID.randomUUID()).name("G").code("G1").status(AmbassadorStatus.ACTIVE).commissionPercent(new BigDecimal("20")).build();
        when(repo.findById(a.getId())).thenReturn(Optional.of(a));
        assertThrows(BadRequestException.class, () -> service.markPaid(a.getId(), java.time.YearMonth.now().toString(), "admin"));   // not finished
        assertThrows(BadRequestException.class, () -> service.markPaid(a.getId(), "oops", "admin"));
        when(stats.revenueByMonth(eq(a.getId()), any())).thenReturn(List.of());
        assertEquals("IN_PROGRESS", service.payoutRows(a).get(0).getStatus());
    }
}
