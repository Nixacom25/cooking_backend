package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.AcquisitionResponse;
import com.cooked.backend.dto.response.TrendsResponse;
import com.cooked.backend.dto.response.TrendsResponse.Trend;
import com.cooked.backend.entity.ProductEventType;
import com.cooked.backend.entity.RecipeOrigin;
import com.cooked.backend.entity.Role;
import com.cooked.backend.entity.TrendingDish;
import com.cooked.backend.repository.TrendQueryRepository;
import com.cooked.backend.repository.TrendQueryRepository.LabelCount;
import com.cooked.backend.repository.TrendingDishRepository;
import com.cooked.backend.service.AdminTrendsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

/** Compares each label's count with the previous window of the same length (aggregate queries only). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminTrendsServiceImpl implements AdminTrendsService {

    static final int TOP = 8;
    /** Candidates fetched before ranking by growth. */
    static final int CANDIDATES = 40;

    private final TrendQueryRepository trends;
    private final TrendingDishRepository trendingDishRepository;

    @Override
    public TrendsResponse trends(int days) {
        return trendsAt(Math.max(1, Math.min(days, MAX_DAYS)), LocalDate.now());
    }

    TrendsResponse trendsAt(int days, LocalDate today) {
        LocalDateTime to = today.plusDays(1).atStartOfDay();
        LocalDateTime from = to.minusDays(days);
        LocalDateTime prevFrom = from.minusDays(days);
        PageRequest candidates = PageRequest.of(0, CANDIDATES);

        List<Trend> searches = rank(
                trends.eventDetailCounts(ProductEventType.WEB_SEARCH, from, to, candidates),
                labels -> trends.eventDetailCountsIn(ProductEventType.WEB_SEARCH, labels, prevFrom, from));
        List<Trend> ingredients = rank(
                trends.ingredientCounts(RecipeOrigin.SCAN, Role.CLIENT, from, to, candidates),
                labels -> trends.ingredientCountsIn(RecipeOrigin.SCAN, labels, Role.CLIENT, prevFrom, from));
        List<Trend> recipes = rank(
                trends.recipeNameCounts(Role.CLIENT, from, to, candidates),
                labels -> trends.recipeNameCountsIn(labels, Role.CLIENT, prevFrom, from));
        List<LabelCount> prevCategories = trends.categoryCounts(Role.CLIENT, prevFrom, from);
        List<Trend> categories = rank(trends.categoryCounts(Role.CLIENT, from, to), labels -> prevCategories);

        return TrendsResponse.builder()
                .days(days)
                .trendingDishes(trendingDishRepository.findAll().stream().map(TrendingDish::getName).filter(Objects::nonNull).limit(TOP).toList())
                .focus(searches.isEmpty() ? null : focus(searches.get(0), days, from, prevFrom, to))
                .searches(searches)
                .ingredients(ingredients)
                .recipes(recipes)
                .categories(categories)
                .build();
    }

    private TrendsResponse.Focus focus(Trend top, int days, LocalDateTime from, LocalDateTime prevFrom, LocalDateTime to) {
        return TrendsResponse.Focus.builder()
                .label(top.getLabel()).total(top.getTotal()).previous(top.getPrevious()).growth(top.getGrowth())
                .daily(series(from.toLocalDate(), days, trends.eventDetailDaily(ProductEventType.WEB_SEARCH, top.getLabel(), from, to)))
                .dailyPrev(series(prevFrom.toLocalDate(), days, trends.eventDetailDaily(ProductEventType.WEB_SEARCH, top.getLabel(), prevFrom, from)))
                .build();
    }

    /**
     * Joins current counts with previous ones and keeps the top {@value #TOP}:
     * growing labels first (by growth, then volume), new ones count as growing.
     */
    static List<Trend> rank(List<LabelCount> current, Function<Collection<String>, List<LabelCount>> previous) {
        if (current.isEmpty()) return List.of();
        Map<String, Long> prev = new HashMap<>();
        previous.apply(current.stream().map(LabelCount::getLabel).toList())
                .forEach(p -> prev.merge(p.getLabel(), nz(p.getTotal()), Long::sum));
        List<Trend> out = new ArrayList<>();
        for (LabelCount c : current) {
            if (c.getLabel() == null || c.getLabel().isBlank()) continue;
            long now = nz(c.getTotal());
            long before = prev.getOrDefault(c.getLabel(), 0L);
            out.add(Trend.builder().label(c.getLabel()).total(now).previous(before).growth(growth(now, before)).build());
        }
        out.sort(Comparator.comparingDouble((Trend t) -> t.getGrowth() == null ? Double.MAX_VALUE : t.getGrowth()).reversed()
                .thenComparing(Comparator.comparingLong(Trend::getTotal).reversed()));
        return out.subList(0, Math.min(TOP, out.size()));
    }

    static Double growth(long now, long before) {
        return before == 0 ? null : Math.round(1000.0 * (now - before) / before) / 10.0;
    }

    static List<AcquisitionResponse.DayCount> series(LocalDate first, int days, List<TrendQueryRepository.DayCount> rows) {
        Map<LocalDate, Long> byDay = new HashMap<>();
        rows.forEach(r -> byDay.merge(r.getDay(), nz(r.getTotal()), Long::sum));
        List<AcquisitionResponse.DayCount> out = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            LocalDate d = first.plusDays(i);
            out.add(new AcquisitionResponse.DayCount(d.toString(), byDay.getOrDefault(d, 0L)));
        }
        return out;
    }

    private static long nz(Long v) {
        return v == null ? 0 : v;
    }
}
