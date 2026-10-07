package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.FeatureCostResponse;
import com.cooked.backend.dto.response.ProductAnalyticsResponse;
import com.cooked.backend.service.AdminAnalyticsService;
import com.cooked.backend.service.AdminCostService;
import com.cooked.backend.service.FeatureCostService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Splits "AI & APIs" spend between scan, import and web search in proportion to
 * calls × weight (cost.weight.* — e.g. a scan sends an image, so it may cost more
 * than a search). Estimates only: replace with real per-call costs if the AI
 * service ever reports them.
 */
@Service
public class FeatureCostServiceImpl implements FeatureCostService {

    private final AdminCostService costs;
    private final AdminAnalyticsService analytics;
    private final Map<String, Double> weights = new LinkedHashMap<>();

    public FeatureCostServiceImpl(AdminCostService costs, AdminAnalyticsService analytics,
                                  @Value("${cost.weight.scan:1}") double scan,
                                  @Value("${cost.weight.import:1}") double imp,
                                  @Value("${cost.weight.search:1}") double search) {
        this.costs = costs;
        this.analytics = analytics;
        weights.put("SCAN", scan);
        weights.put("IMPORT", imp);
        weights.put("WEB_SEARCH", search);
    }

    @Override
    public FeatureCostResponse features(int days) {
        int d = Math.max(1, Math.min(days, 45));                 // previous window must fit in 90 days
        double[] spend = costs.aiSpend(d);
        ProductAnalyticsResponse both = analytics.product(2 * d);
        Map<String, long[]> calls = new LinkedHashMap<>();       // type -> {current, previous}
        weights.keySet().forEach(k -> calls.put(k, new long[2]));
        List<ProductAnalyticsResponse.DailyCounts> daily = both.getDaily();
        for (int i = 0; i < daily.size(); i++) {
            int slot = i < daily.size() - d ? 1 : 0;
            ProductAnalyticsResponse.DailyCounts c = daily.get(i);
            calls.get("SCAN")[slot] += c.getScans();
            calls.get("IMPORT")[slot] += c.getImports();
            calls.get("WEB_SEARCH")[slot] += c.getSearches();
        }
        return FeatureCostResponse.builder()
                .days(d).aiSpend(spend[0]).aiSpendPrev(spend[1])
                .basis(String.format(java.util.Locale.US, "Estimated: AI & APIs spend split by calls × weight (scan %.1f, import %.1f, search %.1f)",
                        weights.get("SCAN"), weights.get("IMPORT"), weights.get("WEB_SEARCH")))
                .features(allocate(spend, calls, weights))
                .build();
    }

    static List<FeatureCostResponse.Feature> allocate(double[] spend, Map<String, long[]> calls, Map<String, Double> weights) {
        double units = 0, unitsPrev = 0;
        for (Map.Entry<String, long[]> e : calls.entrySet()) {
            units += e.getValue()[0] * weights.get(e.getKey());
            unitsPrev += e.getValue()[1] * weights.get(e.getKey());
        }
        List<FeatureCostResponse.Feature> out = new ArrayList<>();
        for (Map.Entry<String, long[]> e : calls.entrySet()) {
            double w = weights.get(e.getKey());
            double share = units == 0 ? 0 : e.getValue()[0] * w / units;
            double cost = spend[0] * share;
            double costPrev = unitsPrev == 0 ? 0 : spend[1] * e.getValue()[1] * w / unitsPrev;
            out.add(FeatureCostResponse.Feature.builder()
                    .feature(e.getKey()).requests(e.getValue()[0]).requestsPrev(e.getValue()[1]).weight(w)
                    .cost(round(cost)).costPrev(round(costPrev)).share(round(100 * share))
                    .costPerUse(e.getValue()[0] == 0 ? null : Math.round(cost / e.getValue()[0] * 10000) / 10000.0)
                    .build());
        }
        out.sort((a, b) -> Double.compare(b.getCost(), a.getCost()));
        return out;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
