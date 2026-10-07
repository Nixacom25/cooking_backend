package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.FeatureCostResponse;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FeatureCostServiceImplTest {

    @Test
    void spendIsSplitByWeightedCalls() {
        Map<String, long[]> calls = new LinkedHashMap<>();
        calls.put("SCAN", new long[] {100, 50});
        calls.put("IMPORT", new long[] {100, 0});
        calls.put("WEB_SEARCH", new long[] {0, 50});
        Map<String, Double> weights = Map.of("SCAN", 3.0, "IMPORT", 1.0, "WEB_SEARCH", 1.0);

        var out = FeatureCostServiceImpl.allocate(new double[] {40, 10}, calls, weights);
        FeatureCostResponse.Feature scan = out.get(0);
        assertEquals("SCAN", scan.getFeature());
        assertEquals(30.0, scan.getCost());          // 300 / 400 units of $40
        assertEquals(75.0, scan.getShare());
        assertEquals(0.3, scan.getCostPerUse());
        assertEquals(7.5, scan.getCostPrev());       // 150 / 200 units of $10
        var search = out.stream().filter(f -> f.getFeature().equals("WEB_SEARCH")).findFirst().orElseThrow();
        assertNull(search.getCostPerUse());
        assertEquals(0.0, search.getCost());
    }

    @Test
    void nothingToSplit() {
        Map<String, long[]> calls = new LinkedHashMap<>();
        calls.put("SCAN", new long[] {0, 0});
        var out = FeatureCostServiceImpl.allocate(new double[] {0, 0}, calls, Map.of("SCAN", 1.0));
        assertEquals(0.0, out.get(0).getCost());
    }
}
