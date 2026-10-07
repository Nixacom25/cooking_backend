package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.CreateCostRequest;
import com.cooked.backend.dto.response.CostOverviewResponse;
import com.cooked.backend.entity.*;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.repository.*;
import com.cooked.backend.service.CurrencyConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminCostServiceImplTest {

    private final CostEntryRepository entries = mock(CostEntryRepository.class);
    private final ProviderBudgetRepository budgets = mock(ProviderBudgetRepository.class);
    private final ProviderDailyCostRepository daily = mock(ProviderDailyCostRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final UserActivityDayRepository activity = mock(UserActivityDayRepository.class);
    private AdminCostServiceImpl service;

    private final LocalDate today = LocalDate.of(2026, 10, 10);

    @BeforeEach
    void setUp() {
        service = new AdminCostServiceImpl(entries, budgets, daily, users, activity,
                new CurrencyConverter(new BigDecimal("1.10"), new BigDecimal("0.70"), new BigDecimal("1.30")), List.of());
    }

    private static ProviderDailyCostRepository.ProviderDayAmount synced(String provider, LocalDate day, String usd) {
        return new ProviderDailyCostRepository.ProviderDayAmount() {
            public String getProvider() { return provider; }
            public LocalDate getDay() { return day; }
            public BigDecimal getAmount() { return new BigDecimal(usd); }
        };
    }

    @Test
    void overviewCombinesManualAndSyncedCosts() {
        CostEntry render = CostEntry.builder().provider("Render").category(CostCategory.INFRASTRUCTURE)
                .amount(new BigDecimal("310")).currency("USD").amountUsd(new BigDecimal("310"))
                .frequency(CostFrequency.MONTHLY).startDate(LocalDate.of(2026, 1, 1)).build();
        when(entries.findOverlapping(any(), any())).thenReturn(List.of(render));
        when(daily.sumByProviderAndDay(any(), any())).thenReturn(List.of(
                synced("OpenAI", today, "5"), synced("OpenAI", today.minusDays(1), "5")));
        when(budgets.findAll()).thenReturn(List.of(ProviderBudget.builder().provider("OpenAI")
                .monthlyBudgetUsd(new BigDecimal("100")).creditBalance(new BigDecimal("20")).creditUnit("USD").build()));
        when(activity.countDistinctUsers(any(), any())).thenReturn(10L);
        when(users.countByRoleAndSubscriptionStatusIn(any(), any())).thenReturn(4L);

        CostOverviewResponse o = service.overviewAt(7, today);

        assertEquals(10.0 + 10 * 10, o.getMonthToDate(), 0.01);      // OpenAI 10 + Render 10/day × 10 days
        assertEquals(15.0, o.getCostToday(), 0.01);
        assertEquals(o.getMonthToDate() / 10, o.getCostPerActiveUser(), 0.01);
        assertEquals(7, o.getDaily().size());
        assertEquals("Infrastructure", o.getByCategory().get(0).getLabel());

        CostOverviewResponse.ProviderRow openAi = o.getProviders().stream().filter(p -> p.getProvider().equals("OpenAI")).findFirst().orElseThrow();
        assertEquals(31.0, openAi.getProjectedMonth(), 0.01);          // 10 so far → 10 × 31 / 10
        assertEquals("SYNCED", openAi.getSource());
        assertEquals(5.0, openAi.getToday(), 0.01);
        assertEquals(14, openAi.getCreditDaysLeft());                  // $20 at 10/7 per day
        assertEquals("HEALTHY", openAi.getStatus());

        CostOverviewResponse.ProviderRow host = o.getProviders().stream().filter(p -> p.getProvider().equals("Render")).findFirst().orElseThrow();
        assertEquals(310.0, host.getProjectedMonth(), 0.01);
        assertEquals("NO_BUDGET", host.getStatus());
    }

    @Test
    void statusAndAlerts() {
        assertEquals("LOW_CREDITS", AdminCostServiceImpl.status(3, 100.0, 10));
        assertEquals("OVER_BUDGET", AdminCostServiceImpl.status(null, 100.0, 120));
        assertEquals("HEALTHY", AdminCostServiceImpl.status(30, 100.0, 80));
        var alerts = AdminCostServiceImpl.alerts(List.of(CostOverviewResponse.ProviderRow.builder()
                .provider("Images").status("LOW_CREDITS").creditDaysLeft(5).build()));
        assertEquals("Credits running low", alerts.get(0).getTitle());
    }

    @Test
    void manualCostIsConvertedToUsd() {
        when(budgets.findByProviderIgnoreCase("render")).thenReturn(java.util.Optional.of(ProviderBudget.builder().provider("Render").build()));
        when(entries.save(any())).thenAnswer(i -> i.getArgument(0));
        CreateCostRequest r = new CreateCostRequest();
        r.setProvider("render"); r.setCategory(CostCategory.INFRASTRUCTURE); r.setAmount(new BigDecimal("100"));
        r.setCurrency("eur"); r.setFrequency(CostFrequency.MONTHLY); r.setStartDate(today);

        var saved = service.addEntry(r, "admin@cooked.app");
        assertEquals("Render", saved.getProvider());                  // existing spelling reused
        assertEquals(new BigDecimal("110.00"), saved.getAmountUsd());
        assertEquals("EUR", saved.getCurrency());

        r.setEndDate(today.minusDays(1));
        assertThrows(BadRequestException.class, () -> service.addEntry(r, "admin@cooked.app"));
        r.setEndDate(null); r.setCurrency("JPY");
        assertThrows(BadRequestException.class, () -> service.addEntry(r, "admin@cooked.app"));
    }
}
