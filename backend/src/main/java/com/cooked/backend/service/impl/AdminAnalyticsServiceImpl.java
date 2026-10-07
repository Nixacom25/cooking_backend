package com.cooked.backend.service.impl;

import java.util.UUID;
import com.cooked.backend.entity.User;
import com.cooked.backend.repository.spec.AdminUserSpecs;
import com.cooked.backend.dto.request.AnalyticsSegment;
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
    public ProductAnalyticsResponse product(int days, AnalyticsSegment segment) {
        return productAt(clamp(days), LocalDateTime.now(), usersOf(segment));
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
    public EngagementResponse engagement(int days, AnalyticsSegment segment) {
        return engagementAt(clamp(days), LocalDate.now(), usersOf(segment));
    }

    /** Ids of the users in a segment, or null for "everyone" (never an empty list: IN () is invalid). */
    List<UUID> usersOf(AnalyticsSegment segment) {
        if (segment == null || segment.all()) return null;
        List<UUID> ids = userRepository.findAll(AdminUserSpecs.of(segment.toUserFilter(), LocalDateTime.now())).stream().map(User::getId).toList();
        return ids.isEmpty() ? List.of(NOBODY) : ids;
    }

    private static final UUID NOBODY = new UUID(0L, 0L);

    @Override
    public com.cooked.backend.dto.response.RetentionResponse retention(String by, int days) {
        int d = Math.max(7, Math.min(days, 180));
        String dim = by == null || by.isBlank() ? "week" : by.trim().toLowerCase(java.util.Locale.ROOT);
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(d - 1L);

        var cohort = userRepository.findCohort(from.atStartOfDay());
        List<UUID> ids = cohort.stream().map(UserRepository.CohortUser::getId).toList();
        Map<UUID, java.util.Set<LocalDate>> active = new java.util.HashMap<>();
        if (!ids.isEmpty()) {
            java.util.Set<UUID> idSet = new java.util.HashSet<>(ids);
            activityRepository.activitySince(from).stream().filter(r -> idSet.contains(r.getUserId()))
                    .forEach(r -> active.computeIfAbsent(r.getUserId(), k -> new java.util.HashSet<>()).add(r.getDay()));
        }
        Map<UUID, UserRepository.CohortUser> byId = new java.util.HashMap<>();
        cohort.forEach(u -> byId.put(u.getId(), u));
        java.util.function.Function<RetentionCalculator.Member, String> groupOf = switch (dim) {
            case "source" -> {
                Map<String, String> canonical = new java.util.HashMap<>();
                yield m -> {
                    String raw = byId.get(m.id()).getDiscoverySource();
                    if (raw == null || raw.isBlank()) return "Not answered";
                    return canonical.computeIfAbsent(raw.trim().toLowerCase(java.util.Locale.ROOT), k -> raw.trim());
                };
            }
            case "subscription" -> m -> {
                var st = byId.get(m.id()).getSubscriptionStatus();
                return st == null ? "Free" : st.name().charAt(0) + st.name().substring(1).toLowerCase(java.util.Locale.ROOT);
            };
            case "ambassador" -> m -> byId.get(m.id()).getReferredByAmbassadorId() != null ? "Ambassador code" : "No code";
            case "scan", "import" -> {
                ProductEventType type = dim.equals("scan") ? ProductEventType.SCAN : ProductEventType.IMPORT;
                java.util.Set<UUID> used = ids.isEmpty() ? java.util.Set.of() : new java.util.HashSet<>(eventRepository.usersWhoUsed(type, ids));
                String yes = dim.equals("scan") ? "Scanned a recipe" : "Imported a recipe";
                String no = dim.equals("scan") ? "Never scanned" : "Never imported";
                yield m -> used.contains(m.id()) ? yes : no;
            }
            case "platform" -> {
                Map<UUID, String> platform = new java.util.HashMap<>();
                if (!ids.isEmpty()) {
                    for (Object[] row : userRepository.findDeviceNames(ids)) {
                        String name = row[1] == null ? "" : row[1].toString().toLowerCase(java.util.Locale.ROOT);
                        String p = name.startsWith("ios") ? "iOS" : name.startsWith("android") ? "Android"
                                : name.equals(AdminUserSpecs.MOBILE_APP) ? "App (OS not reported)" : "Web / other";
                        platform.merge((UUID) row[0], p, (a, b) -> a.equals("Web / other") || a.startsWith("App") ? b : a);
                    }
                }
                yield m -> platform.getOrDefault(m.id(), "Unknown");
            }
            default -> m -> RetentionCalculator.weekOf(m.signup());
        };
        List<RetentionCalculator.Member> members = cohort.stream()
                .filter(u -> u.getCreatedAt() != null)
                .map(u -> new RetentionCalculator.Member(u.getId(), u.getCreatedAt().toLocalDate())).toList();
        String label = switch (dim) {
            case "source", "subscription", "ambassador", "scan", "import", "platform" -> dim;
            default -> "week";
        };
        return new com.cooked.backend.dto.response.RetentionResponse(label, d,
                RetentionCalculator.groups(members, active, today, groupOf, label.equals("week")));
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
                .userId(r.getUserId())
                .userName(name.isEmpty() ? null : name)
                .userEmail(r.getEmail())
                .build();
    }

    static int clamp(int days) {
        return Math.max(1, Math.min(days, MAX_DAYS));
    }

    ProductAnalyticsResponse productAt(int days, LocalDateTime now) {
        return productAt(days, now, null);
    }

    /** [users] null = everyone, else only their events. */
    ProductAnalyticsResponse productAt(int days, LocalDateTime now, List<UUID> users) {
        LocalDate firstDay = now.toLocalDate().minusDays(days - 1L);
        LocalDateTime from = firstDay.atStartOfDay();
        PageRequest top = PageRequest.of(0, TOP);
        boolean all = users == null;
        List<RecipeOrigin> origins = List.of(RecipeOrigin.SCAN, RecipeOrigin.IMPORT);

        return ProductAnalyticsResponse.builder()
                .days(days)
                .trackingSince(eventRepository.firstEventAt())
                .summaries(summaries(all ? eventRepository.summaryByType(from) : eventRepository.summaryByTypeForUsers(from, users)))
                .daily(daily(firstDay, days, all ? eventRepository.dailyCounts(from) : eventRepository.dailyCountsForUsers(from, users)))
                .importSources(labels(all ? eventRepository.topDetails(ProductEventType.IMPORT, from, top)
                        : eventRepository.topDetailsForUsers(ProductEventType.IMPORT, from, users, top)))
                .topSearches(labels(all ? eventRepository.topDetails(ProductEventType.WEB_SEARCH, from, top)
                        : eventRepository.topDetailsForUsers(ProductEventType.WEB_SEARCH, from, users, top)))
                .zeroResultSearches(labels(all ? eventRepository.zeroResultSearches(from, top) : eventRepository.zeroResultSearchesForUsers(from, users, top)))
                .failureReasons((all ? eventRepository.topFailureReasons(from, top) : eventRepository.topFailureReasonsForUsers(from, users, top)).stream()
                        .map(r -> new ProductAnalyticsResponse.FailureReason(r.getType(), r.getReason(), nz(r.getTotal())))
                        .toList())
                .recipesCreated(recipesCreated(firstDay, days, all
                        ? recipeRepository.countCreatedByDayAndOrigin(from, origins)
                        : recipeRepository.countCreatedByDayAndOriginForUsers(from, origins, users)))
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
                .bySource(mergeSources(userRepository.countSignupsBySource(Role.CLIENT, from)))
                .build();
    }

    EngagementResponse engagementAt(int days, LocalDate today) {
        return engagementAt(days, today, null);
    }

    /** [users] null = everyone. */
    EngagementResponse engagementAt(int days, LocalDate today, List<UUID> users) {
        LocalDate end = today.plusDays(1);              // exclusive
        LocalDate from = end.minusDays(days);
        LocalDate prevFrom = from.minusDays(days);
        boolean all = users == null;
        java.util.function.BiFunction<LocalDate, LocalDate, List<UserActivityDayRepository.DayCount>> byDay =
                (a, b) -> all ? activityRepository.countByDay(a, b) : activityRepository.countByDayForUsers(a, b, users);
        java.util.function.ToLongBiFunction<LocalDate, LocalDate> distinct =
                (a, b) -> all ? activityRepository.countDistinctUsers(a, b) : activityRepository.countDistinctUsersForUsers(a, b, users);
        List<AcquisitionResponse.DayCount> daily = daySeries(from, days, byDay.apply(from, end));
        List<AcquisitionResponse.DayCount> dailyPrev = daySeries(prevFrom, days, byDay.apply(prevFrom, from));

        return EngagementResponse.builder()
                .days(days)
                .trackingSince(activityRepository.firstDay())
                .totalUsers(all ? userRepository.countByRole(Role.CLIENT) : users.stream().filter(id -> !NOBODY.equals(id)).count())
                .activeUsers(distinct.applyAsLong(from, end))
                .activeUsersPrev(distinct.applyAsLong(prevFrom, from))
                .dau(average(daily))
                .dauPrev(average(dailyPrev))
                .wau(distinct.applyAsLong(end.minusDays(7), end))
                .wauPrev(distinct.applyAsLong(end.minusDays(14), end.minusDays(7)))
                .mau(distinct.applyAsLong(end.minusDays(30), end))
                .mauPrev(distinct.applyAsLong(end.minusDays(60), end.minusDays(30)))
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

    /** Labels that read the same (null / blank → "Not answered", "TIKTOK" / "tiktok") are merged, biggest first. */
    static List<AcquisitionResponse.SourceCount> mergeSources(List<UserRepository.LabelCount> rows) {
        Map<String, Long> merged = new LinkedHashMap<>();
        rows.forEach(r -> merged.merge(sourceLabel(r.getLabel()), nz(r.getTotal()), Long::sum));
        return merged.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new AcquisitionResponse.SourceCount(e.getKey(), e.getValue()))
                .toList();
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
