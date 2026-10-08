package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.SourceDetailResponse;
import com.cooked.backend.entity.SubscriptionStatus;
import com.cooked.backend.repository.AdminInsightsRepository;
import com.cooked.backend.service.AdminInsightsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminInsightsServiceImpl implements AdminInsightsService {

    static final int MAX_IDS = 100;
    private static final Set<SubscriptionStatus> PAYING = EnumSet.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PREMIUM);

    private final AdminInsightsRepository repo;

    @Override
    public List<SourceDetailResponse> sources(int days) {
        int d = Math.max(1, Math.min(days, 365));
        List<Object[]> rows = repo.signupsSince(LocalDate.now().minusDays(d - 1L).atStartOfDay());
        Map<UUID, BigDecimal> revenue = new HashMap<>();
        List<UUID> ids = rows.stream().map(r -> (UUID) r[2]).toList();
        for (int i = 0; i < ids.size(); i += 500) {
            repo.revenueOf(ids.subList(i, Math.min(ids.size(), i + 500))).forEach(r -> revenue.put((UUID) r[0], (BigDecimal) r[1]));
        }
        Map<String, String> canonical = new HashMap<>();
        Map<String, long[]> counts = new LinkedHashMap<>();   // signups, trials, paid
        Map<String, Double> money = new HashMap<>();
        for (Object[] r : rows) {
            String raw = (String) r[0];
            String label = raw == null || raw.isBlank() ? "Not answered" : canonical.computeIfAbsent(raw.trim().toLowerCase(Locale.ROOT), k -> raw.trim());
            long[] c = counts.computeIfAbsent(label, k -> new long[3]);
            c[0]++;
            if (r[1] == SubscriptionStatus.TRIAL) c[1]++;
            BigDecimal paid = revenue.get((UUID) r[2]);
            if (paid != null && paid.signum() > 0) {
                c[2]++;
                money.merge(label, paid.doubleValue(), Double::sum);
            } else if (PAYING.contains(r[1])) {
                c[2]++;
            }
        }
        List<SourceDetailResponse> out = new ArrayList<>();
        counts.forEach((label, c) -> {
            double rev = Math.round(money.getOrDefault(label, 0.0) * 100) / 100.0;
            out.add(new SourceDetailResponse(label, c[0], c[1], c[2], rev, c[0] == 0 ? null : Math.round(rev / c[0] * 100) / 100.0));
        });
        out.sort(Comparator.comparingLong(SourceDetailResponse::signups).reversed());
        return out;
    }

    @Override
    public Map<UUID, Map<String, Long>> creators(Collection<UUID> ids) {
        List<UUID> list = cap(ids);
        Map<UUID, Map<String, Long>> out = emptyFor(list, "savers", "views30d");
        if (list.isEmpty()) return out;
        repo.saversOf(list).forEach(r -> out.get((UUID) r[0]).put("savers", (Long) r[1]));
        repo.creatorViewsSince(list, LocalDateTime.now().minusDays(30)).forEach(r -> out.get((UUID) r[0]).put("views30d", (Long) r[1]));
        return out;
    }

    @Override
    public Map<UUID, Map<String, Long>> recipes(Collection<UUID> ids) {
        List<UUID> list = cap(ids);
        Map<UUID, Map<String, Long>> out = emptyFor(list, "views30d", "cookbooks", "mealPlans");
        if (list.isEmpty()) return out;
        repo.recipeViewsSince(list.stream().map(UUID::toString).toList(), LocalDateTime.now().minusDays(30))
                .forEach(r -> out.get(UUID.fromString((String) r[0])).put("views30d", (Long) r[1]));
        repo.cookbooksOf(list).forEach(r -> out.get((UUID) r[0]).put("cookbooks", (Long) r[1]));
        repo.mealPlansOf(list).forEach(r -> out.get((UUID) r[0]).put("mealPlans", (Long) r[1]));
        return out;
    }

    @Override
    public Map<String, Long> articleVisitors(int days) {
        Map<String, Long> out = new LinkedHashMap<>();
        repo.blogVisitorsSince(LocalDate.now().minusDays(Math.max(1, Math.min(days, 365)) - 1L)).forEach(r -> out.put((String) r[0], (Long) r[1]));
        return out;
    }

    @Override
    public Map<String, Long> payingUsersByFeature(int days) {
        Map<String, Long> out = new LinkedHashMap<>();
        repo.payingUsersByTypeSince(LocalDate.now().minusDays(Math.max(1, Math.min(days, 365)) - 1L).atStartOfDay())
                .forEach(r -> out.put(String.valueOf(r[0]), (Long) r[1]));
        return out;
    }

    private static List<UUID> cap(Collection<UUID> ids) {
        if (ids == null) return List.of();
        if (ids.size() > MAX_IDS) throw new com.cooked.backend.exception.BadRequestException("At most " + MAX_IDS + " ids");
        return new ArrayList<>(new LinkedHashSet<>(ids));
    }

    private static Map<UUID, Map<String, Long>> emptyFor(List<UUID> ids, String... keys) {
        Map<UUID, Map<String, Long>> out = new LinkedHashMap<>();
        for (UUID id : ids) {
            Map<String, Long> m = new LinkedHashMap<>();
            for (String k : keys) m.put(k, 0L);
            out.put(id, m);
        }
        return out;
    }
}
