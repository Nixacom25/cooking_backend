package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.RevenueSummaryResponse;
import com.cooked.backend.entity.SubscriptionPlan;
import com.cooked.backend.repository.SubscriptionPaymentRepository;
import com.cooked.backend.repository.SubscriptionPaymentRepository.LabelAmount;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminRevenueServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private SubscriptionPaymentRepository paymentRepository;
    @Mock private SubscriptionService subscriptionService;
    @InjectMocks private AdminRevenueServiceImpl service;

    private static LabelAmount row(String label, String amount) {
        return new LabelAmount() {
            public String getLabel() { return label; }
            public BigDecimal getAmount() { return new BigDecimal(amount); }
        };
    }

    @Test
    void planLabelsAreMergedAndSortedByAmount() {
        Map<String, Double> byPlan = AdminRevenueServiceImpl.sortedByAmount(
                List.of(row("MONTHLY", "10"), row("YEARLY", "30"), row("GIFT_YEARLY", "5"), row("yearly", "2")),
                AdminRevenueServiceImpl::planLabel);
        assertEquals(List.of("Annual", "Monthly", "Gift"), List.copyOf(byPlan.keySet()));
        assertEquals(32.0, byPlan.get("Annual"));
    }

    @Test
    void seriesAreZeroFilled() {
        LocalDate first = LocalDate.of(2026, 9, 7);
        var daily = AdminRevenueServiceImpl.dailySeries(first, List.of());
        assertEquals(AdminRevenueServiceImpl.DAYS, daily.size());
        assertEquals("2026-09-07", daily.get(0).getDate());
        assertEquals("2026-10-06", daily.get(daily.size() - 1).getDate());
        assertTrue(daily.stream().allMatch(d -> d.getAmount() == 0));

        var monthly = AdminRevenueServiceImpl.monthlySeries(java.time.YearMonth.of(2026, 3), List.of());
        assertEquals(AdminRevenueServiceImpl.MONTHS, monthly.size());
        assertEquals("2026-10", monthly.get(monthly.size() - 1).getMonth());
    }

    @Test
    void summaryComputesMrrChurnAndLtv() {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setMonthlyPrice(new BigDecimal("9.99"));
        plan.setYearlyPrice(new BigDecimal("29.99"));
        when(subscriptionService.getPlan()).thenReturn(plan);
        when(userRepository.countByRoleAndSubscriptionStatusInAndSubscriptionType(any(), any(), any()))
                .thenReturn(10L)   // monthly plans
                .thenReturn(12L);  // yearly plans
        when(userRepository.countByRoleAndSubscriptionStatusIn(any(), any()))
                .thenReturn(3L)    // cancelled
                .thenReturn(1L)    // expired
                .thenReturn(22L)   // active subscriptions
                .thenReturn(4L);   // trials
        when(paymentRepository.countDistinctPayers()).thenReturn(20L);
        when(paymentRepository.sumSuccess()).thenReturn(new BigDecimal("500"));
        when(paymentRepository.sumSuccessBetween(any(), any())).thenReturn(new BigDecimal("120"), new BigDecimal("100"));

        RevenueSummaryResponse s = service.summaryAt(LocalDateTime.of(2026, 10, 6, 12, 0));

        assertEquals(129.89, s.getMrr());          // 10 * 9.99 + 12 * 29.99 / 12
        assertEquals(1558.68, s.getArr());
        assertEquals(20.0, s.getChurnRate());       // (3 + 1) / 20
        assertEquals(25.0, s.getLtv());             // 500 / 20
        assertEquals(120.0, s.getRevenue30());
        assertEquals(100.0, s.getRevenuePrev30());
        assertEquals(22L, s.getActiveSubscriptions());
        assertEquals("USD", s.getCurrency());
        assertEquals(AdminRevenueServiceImpl.DAYS, s.getDaily().size());
    }

    @Test
    void periodSetsWindowAndSeriesLength() {
        when(paymentRepository.countDistinctPayers()).thenReturn(0L);
        LocalDateTime now = LocalDateTime.of(2026, 10, 6, 12, 0);

        RevenueSummaryResponse week = service.summaryAt(now, 7);
        assertEquals(7, week.getPeriodDays());
        assertEquals(7, week.getDaily().size());
        assertEquals("2026-09-30", week.getDaily().get(0).getDate());

        assertEquals(7, service.summaryAt(now, 1).getDaily().size());       // short windows still show a week
        assertEquals(90, service.summaryAt(now, 365).getDaily().size());    // long windows are capped
        assertEquals(365, service.summaryAt(now, 10_000).getPeriodDays());  // clamped
        assertEquals(1, service.summaryAt(now, -5).getPeriodDays());
    }

    @Test
    void noPaymentsGivesNullChurnAndLtv() {
        when(paymentRepository.countDistinctPayers()).thenReturn(0L);
        RevenueSummaryResponse s = service.summaryAt(LocalDateTime.of(2026, 10, 6, 12, 0));
        assertNull(s.getChurnRate());
        assertNull(s.getLtv());
        assertEquals(0.0, s.getMrr());
    }

    @Test
    void paymentStatusCanBeCorrected() {
        java.util.UUID id = java.util.UUID.randomUUID();
        com.cooked.backend.entity.SubscriptionPayment p = new com.cooked.backend.entity.SubscriptionPayment();
        p.setStatus("SUCCESS");
        org.mockito.Mockito.when(paymentRepository.findById(id)).thenReturn(java.util.Optional.of(p));
        var r = service.setPaymentStatus(id, "SANDBOX", "admin@x.com");
        assertEquals("SUCCESS", r.previousStatus());
        assertEquals("SANDBOX", p.getStatus());
        org.mockito.Mockito.verify(paymentRepository).save(p);
        org.junit.jupiter.api.Assertions.assertThrows(com.cooked.backend.exception.ResourceNotFoundException.class,
                () -> service.setPaymentStatus(java.util.UUID.randomUUID(), "SANDBOX", "admin@x.com"));
    }
}
