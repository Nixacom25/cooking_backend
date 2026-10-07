package com.cooked.backend.repository;

import com.cooked.backend.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** Cost Center queries on a real (H2) database. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CostQueriesTest {

    @Autowired private CostEntryRepository entries;
    @Autowired private ProviderDailyCostRepository daily;
    @Autowired private ProviderBudgetRepository budgets;

    private final LocalDate d = LocalDate.of(2026, 10, 6);

    @Test
    void overlappingEntriesAndDailyAggregates() {
        entries.save(CostEntry.builder().provider("Render").category(CostCategory.INFRASTRUCTURE).amount(BigDecimal.TEN)
                .currency("USD").amountUsd(BigDecimal.TEN).frequency(CostFrequency.MONTHLY).startDate(d.minusMonths(3)).build());
        entries.save(CostEntry.builder().provider("Old").category(CostCategory.OTHER).amount(BigDecimal.ONE)
                .currency("USD").amountUsd(BigDecimal.ONE).frequency(CostFrequency.MONTHLY).startDate(d.minusMonths(6)).endDate(d.minusMonths(4)).build());
        daily.save(ProviderDailyCost.builder().provider("OpenAI").costDay(d).lineItem("gpt-4o-mini, input").amountUsd(new BigDecimal("1.5")).build());
        daily.save(ProviderDailyCost.builder().provider("OpenAI").costDay(d).lineItem("gpt-4o-mini, output").amountUsd(new BigDecimal("2.5")).build());
        daily.save(ProviderDailyCost.builder().provider("OpenAI").costDay(d.minusDays(1)).lineItem("gpt-4o-mini, output").amountUsd(BigDecimal.ONE).build());
        budgets.save(ProviderBudget.builder().provider("OpenAI").monthlyBudgetUsd(new BigDecimal("100")).build());
        daily.flush();

        assertEquals(1, entries.findOverlapping(d.minusDays(30), d).size());
        var byDay = daily.sumByProviderAndDay(d.minusDays(7), d);
        assertEquals(2, byDay.size());
        var items = daily.sumByLineItem("openai", d.minusDays(7), d);
        assertEquals("gpt-4o-mini, output", items.get(0).getLabel());
        assertEquals(0, new BigDecimal("3.5").compareTo(items.get(0).getAmount()));
        assertEquals(d, daily.lastDay());
        assertTrue(budgets.findByProviderIgnoreCase("OPENAI").isPresent());
        assertEquals(2, daily.deleteFrom("OpenAI", d));
    }
}
