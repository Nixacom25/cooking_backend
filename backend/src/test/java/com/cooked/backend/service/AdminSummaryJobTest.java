package com.cooked.backend.service;

import com.cooked.backend.entity.WorkspaceSettings;
import com.cooked.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminSummaryJobTest {

    private final WorkspaceSettingsService settings = mock(WorkspaceSettingsService.class);
    private final AdminSummaryBuilder builder = mock(AdminSummaryBuilder.class);
    private final EmailService email = mock(EmailService.class);
    private final SlackNotifier slack = mock(SlackNotifier.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AdminSummaryJob job = new AdminSummaryJob(settings, builder, email, slack, users);
    private WorkspaceSettings s;

    @BeforeEach
    void setUp() {
        s = WorkspaceSettings.defaults();
        s.setTimezone("Europe/Paris");
        s.setDailySummary(true);
        s.setWeeklyReview(true);
        when(settings.current()).thenReturn(s);
        when(users.findEmailsByRole(any())).thenReturn(List.of("a@cooked.app", "b@cooked.app"));
        when(builder.daily()).thenReturn(new AdminSummaryBuilder.Summary("Sun 4 Oct", List.<String[]>of(new String[] {"Scans", "3"})));
        when(builder.weekly()).thenReturn(new AdminSummaryBuilder.Summary("28 Sep – 4 Oct", List.<String[]>of()));
    }

    @Test
    void sendsAt8InTheWorkspaceTimezoneOnce() {
        job.runAt(ZonedDateTime.parse("2026-10-05T05:00:00Z"));          // 07:00 Paris: nothing
        verifyNoInteractions(email);

        job.runAt(ZonedDateTime.parse("2026-10-05T06:00:00Z"));          // 08:00 Paris, a Monday
        verify(email, times(2)).sendAdminSummaryEmail(anyString(), contains("Daily summary"), anyString(), anyList());
        verify(email, times(2)).sendAdminSummaryEmail(anyString(), contains("Weekly review"), anyString(), anyList());
        verify(settings).markDailySummarySent(LocalDate.of(2026, 10, 5));
        verify(slack, times(2)).post(anyString());

        s.setLastDailySummaryOn(LocalDate.of(2026, 10, 5));
        s.setLastWeeklyReviewOn(LocalDate.of(2026, 10, 5));
        job.runAt(ZonedDateTime.parse("2026-10-05T06:00:00Z"));
        verify(email, times(4)).sendAdminSummaryEmail(anyString(), anyString(), anyString(), anyList());   // not again
    }

    @Test
    void nothingWhenDisabled() {
        s.setDailySummary(false);
        s.setWeeklyReview(false);
        job.runAt(ZonedDateTime.parse("2026-10-05T06:00:00Z"));
        verifyNoInteractions(email, slack);
    }
}
