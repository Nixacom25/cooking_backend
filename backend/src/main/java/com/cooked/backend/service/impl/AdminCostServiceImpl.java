package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.CreateCostRequest;
import com.cooked.backend.dto.request.UpdateProviderBudgetRequest;
import com.cooked.backend.dto.response.CostEntryResponse;
import com.cooked.backend.dto.response.CostOverviewResponse;
import com.cooked.backend.dto.response.CostOverviewResponse.Alert;
import com.cooked.backend.dto.response.CostOverviewResponse.DayAmount;
import com.cooked.backend.dto.response.CostOverviewResponse.LabelAmount;
import com.cooked.backend.dto.response.CostOverviewResponse.ProviderRow;
import com.cooked.backend.dto.response.ProviderCostDetailResponse;
import com.cooked.backend.entity.CostCategory;
import com.cooked.backend.entity.CostEntry;
import com.cooked.backend.entity.ProviderBudget;
import com.cooked.backend.entity.Role;
import com.cooked.backend.entity.SubscriptionStatus;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.CostEntryRepository;
import com.cooked.backend.repository.ProviderBudgetRepository;
import com.cooked.backend.repository.ProviderDailyCostRepository;
import com.cooked.backend.repository.UserActivityDayRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.AdminCostService;
import com.cooked.backend.service.CurrencyConverter;
import com.cooked.backend.service.ProviderBillingSync;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;

/**
 * Builds the Cost Center from two sources: manual {@link CostEntry} rows
 * (spread per day by {@link CostAllocation}) and daily spend imported by
 * {@link ProviderBillingSync} beans. All amounts are USD.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCostServiceImpl implements AdminCostService {

    static final int LOW_CREDIT_DAYS = 7;
    private static final List<SubscriptionStatus> PAID = List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PREMIUM);

    private final CostEntryRepository entryRepository;
    private final ProviderBudgetRepository budgetRepository;
    private final ProviderDailyCostRepository dailyCostRepository;
    private final UserRepository userRepository;
    private final UserActivityDayRepository activityRepository;
    private final CurrencyConverter currencyConverter;
    private final List<ProviderBillingSync> billingSyncs;

    // ---- reads -------------------------------------------------------------

    @Override
    public CostOverviewResponse overview(int days) {
        return overviewAt(clamp(days), LocalDate.now());
    }

    CostOverviewResponse overviewAt(int days, LocalDate today) {
        Window w = new Window(days, today);
        Ledger ledger = ledger(w);
        List<ProviderBudget> budgets = budgetRepository.findAll();

        List<ProviderRow> rows = providerRows(ledger, budgets, w);
        double mtd = ledger.total(w.monthStart, today, p -> true);
        long mau = activityRepository.countDistinctUsers(today.minusDays(29), today.plusDays(1));
        long paid = userRepository.countByRoleAndSubscriptionStatusIn(Role.CLIENT, PAID);
        double budgetSum = budgets.stream().filter(b -> b.getMonthlyBudgetUsd() != null).mapToDouble(b -> b.getMonthlyBudgetUsd().doubleValue()).sum();
        boolean anyBudget = budgets.stream().anyMatch(b -> b.getMonthlyBudgetUsd() != null);

        return CostOverviewResponse.builder()
                .days(days)
                .currency("USD")
                .today(today)
                .costToday(round(ledger.total(today, today, p -> true)))
                .costYesterday(round(ledger.total(today.minusDays(1), today.minusDays(1), p -> true)))
                .monthToDate(round(mtd))
                .monthToDatePrev(round(ledger.total(w.lastMonthStart, w.lastMonthSameDay, p -> true)))
                .lastMonthTotal(round(ledger.total(w.lastMonthStart, w.monthStart.minusDays(1), p -> true)))
                .projectedMonth(round(rows.stream().mapToDouble(ProviderRow::getProjectedMonth).sum()))
                .totalBudget(anyBudget ? round(budgetSum) : null)
                .costPerActiveUser(mau == 0 ? null : round(mtd / mau))
                .costPerSubscriber(paid == 0 ? null : round(mtd / paid))
                .daily(ledger.series(w.from, days, p -> true))
                .dailyPrev(ledger.series(w.prevFrom, days, p -> true))
                .byCategory(byCategory(ledger, w))
                .providers(rows)
                .alerts(alerts(rows))
                .openAiSync(billingSyncs.stream().anyMatch(ProviderBillingSync::isConfigured))
                .lastSyncedDay(dailyCostRepository.lastDay())
                .build();
    }

    @Override
    public ProviderCostDetailResponse provider(String provider, int days) {
        return providerAt(provider, clamp(days), LocalDate.now());
    }

    ProviderCostDetailResponse providerAt(String provider, int days, LocalDate today) {
        Window w = new Window(days, today);
        Ledger ledger = ledger(w);
        String name = ledger.canonical(provider)
                .or(() -> budgetRepository.findByProviderIgnoreCase(provider).map(ProviderBudget::getProvider))
                .orElseThrow(() -> new ResourceNotFoundException("No cost recorded for provider " + provider));
        Predicate<String> only = name::equals;
        ProviderRow summary = providerRows(ledger, budgetRepository.findAll(), w).stream()
                .filter(r -> r.getProvider().equals(name)).findFirst().orElse(null);

        double total = ledger.total(w.from, today, only);
        List<ProviderCostDetailResponse.LineItem> items = new ArrayList<>();
        dailyCostRepository.sumByLineItem(name, w.from, today)
                .forEach(l -> items.add(new ProviderCostDetailResponse.LineItem(l.getLabel(), l.getAmount().doubleValue(), 0)));
        for (CostEntry e : ledger.entries) {
            if (!e.getProvider().equals(name)) continue;
            double amount = 0;
            for (LocalDate d = w.from; !d.isAfter(today); d = d.plusDays(1)) amount += CostAllocation.on(e, d).doubleValue();
            if (amount > 0) items.add(new ProviderCostDetailResponse.LineItem(entryLabel(e), amount, 0));
        }
        items.forEach(i -> {
            i.setShare(total == 0 ? 0 : round(100 * i.getAmount() / total));
            i.setAmount(round(i.getAmount()));
        });
        items.sort(Comparator.comparingDouble(ProviderCostDetailResponse.LineItem::getAmount).reversed());

        return ProviderCostDetailResponse.builder()
                .provider(name)
                .days(days)
                .periodTotal(round(total))
                .periodTotalPrev(round(ledger.total(w.prevFrom, w.from.minusDays(1), only)))
                .summary(summary)
                .daily(ledger.series(w.from, days, only))
                .dailyPrev(ledger.series(w.prevFrom, days, only))
                .lineItems(items)
                .build();
    }

    @Override
    public List<CostEntryResponse> entries() {
        return entryRepository.findAllByOrderByStartDateDesc().stream().map(AdminCostServiceImpl::toResponse).toList();
    }

    // ---- writes ------------------------------------------------------------

    @Override
    @Transactional
    public CostEntryResponse addEntry(CreateCostRequest r, String adminEmail) {
        if (r.getEndDate() != null && r.getEndDate().isBefore(r.getStartDate())) {
            throw new BadRequestException("End date must be on or after the start date");
        }
        String provider = budgetRepository.findByProviderIgnoreCase(r.getProvider().trim())
                .map(ProviderBudget::getProvider).orElse(r.getProvider().trim());
        CostEntry saved = entryRepository.save(CostEntry.builder()
                .provider(provider)
                .category(r.getCategory())
                .amount(r.getAmount())
                .currency(r.getCurrency().toUpperCase(Locale.ROOT))
                .amountUsd(currencyConverter.toUsd(r.getAmount(), r.getCurrency()))
                .frequency(r.getFrequency())
                .startDate(r.getStartDate())
                .endDate(r.getEndDate())
                .notes(r.getNotes() == null || r.getNotes().isBlank() ? null : r.getNotes().trim())
                .createdBy(adminEmail)
                .build());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteEntry(UUID id) {
        if (!entryRepository.existsById(id)) throw new ResourceNotFoundException("Cost entry not found");
        entryRepository.deleteById(id);
    }

    @Override
    @Transactional
    public ProviderRow updateBudget(String provider, UpdateProviderBudgetRequest r) {
        String name = provider == null ? "" : provider.trim();
        if (name.isEmpty() || name.length() > 80) throw new BadRequestException("Invalid provider name");
        ProviderBudget b = budgetRepository.findByProviderIgnoreCase(name)
                .orElseGet(() -> ProviderBudget.builder().provider(name).build());
        boolean creditChanged = !Objects.equals(b.getCreditBalance(), r.getCreditBalance());
        b.setCategory(r.getCategory());
        b.setMonthlyBudgetUsd(r.getMonthlyBudgetUsd());
        b.setCreditBalance(r.getCreditBalance());
        b.setCreditUnit(r.getCreditUnit() == null || r.getCreditUnit().isBlank() ? null : r.getCreditUnit().trim());
        if (creditChanged) b.setCreditUpdatedAt(r.getCreditBalance() == null ? null : LocalDateTime.now());
        budgetRepository.save(b);

        Window w = new Window(30, LocalDate.now());
        return providerRows(ledger(w), budgetRepository.findAll(), w).stream()
                .filter(row -> row.getProvider().equalsIgnoreCase(name)).findFirst().orElseThrow();
    }

    /** Not transactional here: each sync owns its transaction and one failure must not stop the others. */
    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public int syncProviders() {
        int rows = 0;
        for (ProviderBillingSync s : billingSyncs) {
            if (!s.isConfigured()) continue;
            try {
                rows += s.sync(35);
            } catch (RuntimeException e) {
                log.warn("Billing sync failed for {}: {}", s.provider(), e.getMessage());
            }
        }
        return rows;
    }

    // ---- computation -------------------------------------------------------

    static int clamp(int days) {
        return Math.max(1, Math.min(days, MAX_DAYS));
    }

    /** Date bounds shared by every computation of one request. */
    static final class Window {
        final LocalDate today, from, prevFrom, monthStart, monthEnd, lastMonthStart, lastMonthSameDay, first;

        Window(int days, LocalDate today) {
            this.today = today;
            this.from = today.minusDays(days - 1L);
            this.prevFrom = from.minusDays(days);
            this.monthStart = today.withDayOfMonth(1);
            this.monthEnd = today.withDayOfMonth(today.lengthOfMonth());
            this.lastMonthStart = monthStart.minusMonths(1);
            LocalDate lastMonthEnd = monthStart.minusDays(1);
            LocalDate same = lastMonthStart.plusDays(today.getDayOfMonth() - 1L);
            this.lastMonthSameDay = same.isAfter(lastMonthEnd) ? lastMonthEnd : same;
            this.first = prevFrom.isBefore(lastMonthStart) ? prevFrom : lastMonthStart;
        }
    }

    /** USD per provider and day over [first, monthEnd]; synced spend only up to today. */
    static final class Ledger {
        final Map<String, Map<LocalDate, Double>> byProvider = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        final Map<String, CostCategory> categories = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        final Map<String, Set<String>> sources = new HashMap<>();
        final List<CostEntry> entries;

        Ledger(List<CostEntry> entries) {
            this.entries = entries;
        }

        void add(String provider, LocalDate day, double amount, String source) {
            if (amount == 0) return;
            String key = canonical(provider).orElse(provider);
            byProvider.computeIfAbsent(key, k -> new HashMap<>()).merge(day, amount, Double::sum);
            sources.computeIfAbsent(key, k -> new HashSet<>()).add(source);
        }

        Optional<String> canonical(String provider) {
            if (provider == null) return Optional.empty();
            return byProvider.keySet().stream().filter(k -> k.equalsIgnoreCase(provider.trim())).findFirst();
        }

        double total(LocalDate from, LocalDate to, Predicate<String> provider) {
            double sum = 0;
            for (Map.Entry<String, Map<LocalDate, Double>> p : byProvider.entrySet()) {
                if (!provider.test(p.getKey())) continue;
                for (Map.Entry<LocalDate, Double> d : p.getValue().entrySet()) {
                    if (!d.getKey().isBefore(from) && !d.getKey().isAfter(to)) sum += d.getValue();
                }
            }
            return sum;
        }

        List<DayAmount> series(LocalDate first, int days, Predicate<String> provider) {
            List<DayAmount> out = new ArrayList<>(days);
            for (int i = 0; i < days; i++) {
                LocalDate d = first.plusDays(i);
                out.add(new DayAmount(d.toString(), round(total(d, d, provider))));
            }
            return out;
        }
    }

    private Ledger ledger(Window w) {
        List<CostEntry> entries = entryRepository.findOverlapping(w.first, w.monthEnd);
        Ledger ledger = new Ledger(entries);
        for (CostEntry e : entries) {
            ledger.categories.putIfAbsent(e.getProvider(), e.getCategory());
            for (LocalDate d = w.first; !d.isAfter(w.monthEnd); d = d.plusDays(1)) {
                ledger.add(e.getProvider(), d, CostAllocation.on(e, d).doubleValue(), "MANUAL");
            }
        }
        dailyCostRepository.sumByProviderAndDay(w.first, w.today).forEach(r -> {
            ledger.categories.putIfAbsent(r.getProvider(), CostCategory.AI_APIS);
            ledger.add(r.getProvider(), r.getDay(), r.getAmount().doubleValue(), "SYNCED");
        });
        return ledger;
    }

    /** Manual entries are known for the whole month; synced spend is extrapolated from the month so far. */
    static double projectedMonth(Ledger ledger, String provider, Window w) {
        double manualMonth = 0;
        for (CostEntry e : ledger.entries) {
            if (!e.getProvider().equalsIgnoreCase(provider)) continue;
            for (LocalDate d = w.monthStart; !d.isAfter(w.monthEnd); d = d.plusDays(1)) manualMonth += CostAllocation.on(e, d).doubleValue();
        }
        double allMtd = ledger.total(w.monthStart, w.today, p -> p.equalsIgnoreCase(provider));
        double manualMtd = 0;
        for (CostEntry e : ledger.entries) {
            if (!e.getProvider().equalsIgnoreCase(provider)) continue;
            for (LocalDate d = w.monthStart; !d.isAfter(w.today); d = d.plusDays(1)) manualMtd += CostAllocation.on(e, d).doubleValue();
        }
        double syncedMtd = allMtd - manualMtd;
        return manualMonth + syncedMtd * w.today.lengthOfMonth() / w.today.getDayOfMonth();
    }

    List<ProviderRow> providerRows(Ledger ledger, List<ProviderBudget> budgets, Window w) {
        Map<String, ProviderBudget> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        budgets.forEach(b -> byName.put(b.getProvider(), b));
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(ledger.byProvider.keySet());
        names.addAll(byName.keySet());

        List<ProviderRow> rows = new ArrayList<>();
        for (String name : names) {
            ProviderBudget b = byName.get(name);
            String key = ledger.canonical(name).orElse(name);
            Predicate<String> only = p -> p.equalsIgnoreCase(name);
            CostCategory category = b != null && b.getCategory() != null ? b.getCategory()
                    : ledger.categories.getOrDefault(name, CostCategory.OTHER);
            double projected = projectedMonth(ledger, name, w);
            Double budget = b == null || b.getMonthlyBudgetUsd() == null ? null : b.getMonthlyBudgetUsd().doubleValue();

            Integer daysLeft = null;
            LocalDate exhaustion = null;
            if (b != null && b.getCreditBalance() != null && "USD".equalsIgnoreCase(b.getCreditUnit())) {
                double perDay = ledger.total(w.today.minusDays(6), w.today, only) / 7;
                if (perDay > 0) {
                    daysLeft = (int) Math.floor(b.getCreditBalance().doubleValue() / perDay);
                    exhaustion = w.today.plusDays(daysLeft);
                }
            }
            Set<String> src = ledger.sources.getOrDefault(key, Set.of());
            rows.add(ProviderRow.builder()
                    .provider(b != null ? b.getProvider() : key)
                    .category(category.getLabel())
                    .source(src.size() > 1 ? "MIXED" : src.isEmpty() ? "MANUAL" : src.iterator().next())
                    .today(round(ledger.total(w.today, w.today, only)))
                    .monthToDate(round(ledger.total(w.monthStart, w.today, only)))
                    .projectedMonth(round(projected))
                    .periodTotal(round(ledger.total(w.from, w.today, only)))
                    .monthlyBudget(budget)
                    .creditBalance(b == null || b.getCreditBalance() == null ? null : b.getCreditBalance().doubleValue())
                    .creditUnit(b == null ? null : b.getCreditUnit())
                    .creditUpdatedAt(b == null ? null : b.getCreditUpdatedAt())
                    .creditDaysLeft(daysLeft)
                    .creditExhaustion(exhaustion)
                    .status(status(daysLeft, budget, projected))
                    .build());
        }
        rows.sort(Comparator.comparingDouble(ProviderRow::getMonthToDate).reversed());
        return rows;
    }

    static String status(Integer creditDaysLeft, Double budget, double projected) {
        if (creditDaysLeft != null && creditDaysLeft <= LOW_CREDIT_DAYS) return "LOW_CREDITS";
        if (budget == null) return "NO_BUDGET";
        return projected > budget ? "OVER_BUDGET" : "HEALTHY";
    }

    static List<Alert> alerts(List<ProviderRow> rows) {
        List<Alert> out = new ArrayList<>();
        for (ProviderRow r : rows) {
            if ("LOW_CREDITS".equals(r.getStatus())) {
                out.add(new Alert("LOW_CREDITS", r.getProvider(), "Credits running low",
                        r.getProvider() + " has about " + r.getCreditDaysLeft() + " days of credits remaining"));
            } else if ("OVER_BUDGET".equals(r.getStatus())) {
                out.add(new Alert("OVER_BUDGET", r.getProvider(), "Spending above budget",
                        String.format(Locale.US, "%s is projected at $%,.0f this month for a $%,.0f budget", r.getProvider(), r.getProjectedMonth(), r.getMonthlyBudget())));
            }
        }
        return out;
    }

    private static List<LabelAmount> byCategory(Ledger ledger, Window w) {
        Map<String, Double> sums = new HashMap<>();
        for (String provider : ledger.byProvider.keySet()) {
            String label = ledger.categories.getOrDefault(provider, CostCategory.OTHER).getLabel();
            sums.merge(label, ledger.total(w.monthStart, w.today, p -> p.equals(provider)), Double::sum);
        }
        return sums.entrySet().stream().filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(e -> new LabelAmount(e.getKey(), round(e.getValue()))).toList();
    }

    static String entryLabel(CostEntry e) {
        String freq = switch (e.getFrequency()) {
            case MONTHLY -> "Monthly";
            case ANNUAL -> "Annual";
            case ONE_TIME -> "One-time";
        };
        return e.getNotes() == null ? freq + " cost" : freq + " · " + e.getNotes();
    }

    private static CostEntryResponse toResponse(CostEntry e) {
        return CostEntryResponse.builder()
                .id(e.getId()).provider(e.getProvider()).category(e.getCategory()).categoryLabel(e.getCategory().getLabel())
                .amount(e.getAmount()).currency(e.getCurrency()).amountUsd(e.getAmountUsd()).frequency(e.getFrequency())
                .startDate(e.getStartDate()).endDate(e.getEndDate()).notes(e.getNotes()).createdBy(e.getCreatedBy())
                .build();
    }

    static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
