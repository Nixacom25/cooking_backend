package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.AcquisitionResponse;
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

    @Override
    public ProductAnalyticsResponse product(int days) {
        return productAt(clamp(days), LocalDateTime.now());
    }

    @Override
    public AcquisitionResponse acquisition(int days) {
        return acquisitionAt(clamp(days), LocalDateTime.now());
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
