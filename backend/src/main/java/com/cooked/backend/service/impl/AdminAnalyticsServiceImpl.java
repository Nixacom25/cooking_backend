package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.AcquisitionResponse;
import com.cooked.backend.dto.response.EngagementResponse;
import com.cooked.backend.dto.response.ProductFailuresResponse;
import com.cooked.backend.dto.response.ProductAnalyticsResponse;
import com.cooked.backend.dto.response.ProductAnalyticsResponse.DailyCounts;
import com.cooked.backend.dto.response.ProductAnalyticsResponse.LabelStats;
import com.cooked.backend.dto.response.ProductAnalyticsResponse.RecipesCreated;
import com.cooked.backend.dto.response.ProductAnalyticsResponse.TypeStats;
import com.cooked.backend.entity.ProductEventType;
import com.cooked.backend.entity.RecipeOrigin;
import com.cooked.backend.entity.Role;
import com.cooked.backend.repository.ProductEventRepository;
import com.cooked.backend.repository.RecipeRepository;
import com.cooked.backend.repository.UserActivityDayRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.AdminAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Admin analytics built only from aggregate queries (no per-row loading). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAnalyticsServiceImpl implements AdminAnalyticsService {

    static final int TOP = 10;

    private final ProductEventRepository eventRepository;
    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;
    private final UserActivityDayRepository activityRepository;

    @Override
    public ProductAnalyticsResponse product(int days) {
        return productAt(clamp(days), LocalDateTime.now());
    }

    @Override
    public AcquisitionResponse acquisition(int days) {
        return acquisitionAt(clamp(days), LocalDateTime.now());
    }

    @Override
    public EngagementResponse engagement(int days) {
        return engagementAt(clamp(days), LocalDate.now());
    }

    @Override
    public ProductFailuresResponse failures(ProductEventType type, int days, int page, int size) {
        int d = clamp(days);
        int p = Math.max(0, page);
        int s = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        LocalDate today = LocalDate.now();
        var rows = eventRepository.failures(type, today.minusDays(d - 1L).atStartOfDay(), PageRequest.of(p, s));
        return ProductFailuresResponse.builder()
                .days(d)
                .failuresToday(eventRepository.countByTypeAndSuccessFalseAndCreatedAtGreaterThanEqual(type, today.atStartOfDay()))
                .totalElements(rows.getTotalElements())
                .totalPages(rows.getTotalPages())
                .page(p)
                .size(s)
                .items(rows.getContent().stream().map(AdminAnalyticsServiceImpl::failureItem).toList())
                .build();
    }

    static ProductFailuresResponse.Item failureItem(ProductEventRepository.FailureRow r) {
        String name = ((r.getFirstname() == null ? "" : r.getFirstname()) + " " + (r.getLastname() == null ? "" : r.getLastname())).trim();
        return ProductFailuresResponse.Item.builder()
                .id(r.getId())
                .source(r.getDetail())
                .reason(r.getReason())
                .durationMs(r.getDurationMs())
                .createdAt(r.getCreatedAt())
                .userName(name.isEmpty() ? null : name)
                .userEmail(r.getEmail())
                .build();
    }

    static int clamp(int days) {
        return Math.max(1, Math.min(days, MAX_DAYS));
    }

    ProductAnalyticsResponse productAt(int days, LocalDateTime now) {
        LocalDate firstDay = now.toLocalDate().minusDays(days - 1L);
        LocalDateTime from = firstDay.atStartOfDay();
        PageRequest top = PageRequest.of(0, TOP);

        return ProductAnalyticsResponse.builder()
                .days(days)
                .trackingSince(eventRepository.firstEventAt())
                .summaries(summaries(eventRepository.summaryByType(from)))
                .daily(daily(firstDay, days, eventRepository.dailyCounts(from)))
                .importSources(labels(eventRepository.topDetails(ProductEventType.IMPORT, from, top)))
                .topSearches(labels(eventRepository.topDetails(ProductEventType.WEB_SEARCH, from, top)))
                .zeroResultSearches(labels(eventRepository.zeroResultSearches(from, top)))
                .failureReasons(eventRepository.topFailureReasons(from, top).stream()
                        .map(r -> new ProductAnalyticsResponse.FailureReason(r.getType(), r.getReason(), nz(r.getTotal())))
                        .toList())
                .recipesCreated(recipesCreated(firstDay, days,
                        recipeRepository.countCreatedByDayAndOrigin(from, List.of(RecipeOrigin.SCAN, RecipeOrigin.IMPORT))))
                .build();
    }

    AcquisitionResponse acquisitionAt(int days, LocalDateTime now) {
        LocalDate firstDay = now.toLocalDate().minusDays(days - 1L);
        LocalDateTime from = firstDay.atStartOfDay();
        LocalDateTime prevFrom = from.minusDays(days);

        Map<LocalDate, Long> byDay = new LinkedHashMap<>();
        userRepository.countSignupsByDay(Role.CLIENT, from).forEach(d -> byDay.merge(d.getDay(), nz(d.getTotal()), Long::sum));
        List<AcquisitionResponse.DayCount> series = new ArrayList<>(days);
        long total = 0;
        for (int i = 0; i < days; i++) {
            LocalDate d = firstDay.plusDays(i);
            long n = byDay.getOrDefault(d, 0L);
            total += n;
            series.add(new AcquisitionResponse.DayCount(d.toString(), n));
        }

        return AcquisitionResponse.builder()
                .days(days)
                .newUsers(total)
                .newUsersPrev(userRepository.countSignupsBetween(Role.CLIENT, prevFrom, from))
                .signupsDaily(series)
                .bySource(userRepository.countSignupsBySource(Role.CLIENT, from).stream()
                        .map(s -> new AcquisitionResponse.SourceCount(sourceLabel(s.getLabel()), nz(s.getTotal())))
                        .toList())
                .build();
    }

    EngagementResponse engagementAt(int days, LocalDate today) {
        LocalDate end = today.plusDays(1);              // exclusive
        LocalDate from = end.minusDays(days);
        LocalDate prevFrom = from.minusDays(days);
        List<AcquisitionResponse.DayCount> daily = daySeries(from, days, activityRepository.countByDay(from, end));
        List<AcquisitionResponse.DayCount> dailyPrev = daySeries(prevFrom, days, activityRepository.countByDay(prevFrom, from));

        return EngagementResponse.builder()
                .days(days)
                .trackingSince(activityRepository.firstDay())
                .totalUsers(userRepository.countByRole(Role.CLIENT))
                .activeUsers(activityRepository.countDistinctUsers(from, end))
                .activeUsersPrev(activityRepository.countDistinctUsers(prevFrom, from))
                .dau(average(daily))
                .dauPrev(average(dailyPrev))
                .wau(activityRepository.countDistinctUsers(end.minusDays(7), end))
                .wauPrev(activityRepository.countDistinctUsers(end.minusDays(14), end.minusDays(7)))
                .mau(activityRepository.countDistinctUsers(end.minusDays(30), end))
                .mauPrev(activityRepository.countDistinctUsers(end.minusDays(60), end.minusDays(30)))
                .daily(daily)
                .dailyPrev(dailyPrev)
                .build();
    }

    static List<AcquisitionResponse.DayCount> daySeries(LocalDate firstDay, int days, List<UserActivityDayRepository.DayCount> rows) {
        Map<LocalDate, Long> byDay = new LinkedHashMap<>();
        rows.forEach(r -> byDay.merge(r.getDay(), nz(r.getTotal()), Long::sum));
        List<AcquisitionResponse.DayCount> series = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            LocalDate d = firstDay.plusDays(i);
            series.add(new AcquisitionResponse.DayCount(d.toString(), byDay.getOrDefault(d, 0L)));
        }
        return series;
    }

    private static double average(List<AcquisitionResponse.DayCount> series) {
        return series.isEmpty() ? 0 : round(series.stream().mapToLong(AcquisitionResponse.DayCount::getTotal).average().orElse(0));
    }

    static List<TypeStats> summaries(List<ProductEventRepository.TypeSummary> rows) {
        Map<ProductEventType, TypeStats> byType = new EnumMap<>(ProductEventType.class);
        for (ProductEventType t : ProductEventType.values()) {
            byType.put(t, TypeStats.builder().type(t).build());
        }
        for (ProductEventRepository.TypeSummary r : rows) {
            long total = nz(r.getTotal());
            long ok = nz(r.getSuccesses());
            byType.put(r.getType(), TypeStats.builder()
                    .type(r.getType())
                    .total(total)
                    .successes(ok)
                    .failures(total - ok)
                    .successRate(total == 0 ? null : round(100.0 * ok / total))
                    .avgDurationMs(r.getAvgDurationMs() == null ? null : round(r.getAvgDurationMs()))
                    .users(nz(r.getUsers()))
                    .build());
        }
        return new ArrayList<>(byType.values());
    }

    static List<DailyCounts> daily(LocalDate firstDay, int days, List<ProductEventRepository.DayTypeCount> rows) {
        Map<LocalDate, DailyCounts> byDay = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            LocalDate d = firstDay.plusDays(i);
            byDay.put(d, DailyCounts.builder().date(d.toString()).build());
        }
        for (ProductEventRepository.DayTypeCount r : rows) {
            DailyCounts c = byDay.get(r.getDay());
            if (c == null) continue;
            long n = nz(r.getTotal());
            boolean failed = !Boolean.TRUE.equals(r.getSuccess());
            switch (r.getType()) {
                case SCAN -> { c.setScans(c.getScans() + n); if (failed) c.setScanFailures(c.getScanFailures() + n); }
                case IMPORT -> { c.setImports(c.getImports() + n); if (failed) c.setImportFailures(c.getImportFailures() + n); }
                case WEB_SEARCH -> { c.setSearches(c.getSearches() + n); if (failed) c.setSearchFailures(c.getSearchFailures() + n); }
            }
        }
        return new ArrayList<>(byDay.values());
    }

    static List<LabelStats> labels(List<ProductEventRepository.LabelCount> rows) {
        return rows.stream().map(r -> {
            long total = nz(r.getTotal());
            long failures = nz(r.getFailures());
            return new LabelStats(r.getLabel(), total, failures, total == 0 ? 0 : round(100.0 * (total - failures) / total));
        }).toList();
    }

    static List<RecipesCreated> recipesCreated(LocalDate firstDay, int days, List<RecipeRepository.DayOriginCount> rows) {
        Map<LocalDate, RecipesCreated> byDay = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            LocalDate d = firstDay.plusDays(i);
            byDay.put(d, RecipesCreated.builder().date(d.toString()).build());
        }
        for (RecipeRepository.DayOriginCount r : rows) {
            RecipesCreated c = byDay.get(r.getDay());
            if (c == null) continue;
            if (r.getOrigin() == RecipeOrigin.SCAN) c.setFromScans(c.getFromScans() + nz(r.getTotal()));
            else if (r.getOrigin() == RecipeOrigin.IMPORT) c.setFromImports(c.getFromImports() + nz(r.getTotal()));
        }
        return new ArrayList<>(byDay.values());
    }

    static String sourceLabel(String raw) {
        if (raw == null || raw.isBlank()) return "Not answered";
        String s = raw.trim().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static long nz(Long v) {
        return v == null ? 0 : v;
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
