package com.cooked.backend.service.impl;

import com.cooked.backend.entity.ProviderDailyCost;
import com.cooked.backend.repository.ProviderDailyCostRepository;
import com.cooked.backend.service.ProviderBillingSync;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Daily spend from the OpenAI Costs API (organization admin key, read-only).
 * Disabled unless OPENAI_ADMIN_KEY is set. Covers the backend's direct OpenAI
 * calls and anything else billed to the same organization.
 */
@Slf4j
@Service
public class OpenAiBillingSync implements ProviderBillingSync {

    static final String PROVIDER = "OpenAI";
    private static final String COSTS_URL = "https://api.openai.com/v1/organization/costs";
    private static final int MAX_PAGES = 10;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ProviderDailyCostRepository repository;
    private final TransactionTemplate transactions;
    private final String adminKey;

    public OpenAiBillingSync(RestTemplate restTemplate, ObjectMapper objectMapper, ProviderDailyCostRepository repository,
                             TransactionTemplate transactions, @Value("${openai.admin-key:}") String adminKey) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.transactions = transactions;
        this.adminKey = adminKey == null ? "" : adminKey.trim();
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public boolean isConfigured() {
        return !adminKey.isEmpty();
    }

    @Override
    public int sync(int days) {
        if (!isConfigured()) return 0;
        LocalDate first = LocalDate.now(ZoneOffset.UTC).minusDays(days - 1L);
        List<ProviderDailyCost> rows = fetch(first, days);   // HTTP outside the transaction
        transactions.executeWithoutResult(tx -> {
            repository.deleteFrom(PROVIDER, first);
            repository.saveAll(rows);
        });
        log.info("OpenAI billing sync: {} rows since {}", rows.size(), first);
        return rows.size();
    }

    private List<ProviderDailyCost> fetch(LocalDate first, int days) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminKey);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        List<ProviderDailyCost> rows = new ArrayList<>();
        String page = null;
        for (int i = 0; i < MAX_PAGES; i++) {
            UriComponentsBuilder uri = UriComponentsBuilder.fromHttpUrl(COSTS_URL)
                    .queryParam("start_time", first.atStartOfDay().toEpochSecond(ZoneOffset.UTC))
                    .queryParam("bucket_width", "1d")
                    .queryParam("group_by", "line_item")
                    .queryParam("limit", Math.min(days, 180));
            if (page != null) uri.queryParam("page", page);
            String body = restTemplate.exchange(uri.build().toUri(), HttpMethod.GET, request, String.class).getBody();
            JsonNode root = read(body);
            rows.addAll(parse(root));
            if (!root.path("has_more").asBoolean(false)) break;
            page = root.path("next_page").asText(null);
            if (page == null) break;
        }
        return rows;
    }

    private JsonNode read(String body) {
        try {
            return objectMapper.readTree(body == null ? "{}" : body);
        } catch (Exception e) {
            throw new IllegalStateException("Unreadable OpenAI costs response", e);
        }
    }

    /** One row per (day, line item); amounts already in USD. */
    static List<ProviderDailyCost> parse(JsonNode root) {
        Map<String, ProviderDailyCost> merged = new HashMap<>();
        for (JsonNode bucket : root.path("data")) {
            LocalDate day = Instant.ofEpochSecond(bucket.path("start_time").asLong()).atZone(ZoneOffset.UTC).toLocalDate();
            for (JsonNode r : bucket.path("results")) {
                BigDecimal value = r.path("amount").path("value").decimalValue();
                if (value.signum() == 0) continue;
                String item = r.path("line_item").asText("");
                String label = item.isBlank() || "null".equals(item) ? "Other usage" : item;
                if (label.length() > 160) label = label.substring(0, 160);
                String key = day + "|" + label;
                ProviderDailyCost row = merged.computeIfAbsent(key, k -> ProviderDailyCost.builder()
                        .provider(PROVIDER).costDay(day).lineItem(k.substring(k.indexOf('|') + 1)).amountUsd(BigDecimal.ZERO).build());
                row.setAmountUsd(row.getAmountUsd().add(value));
            }
        }
        return new ArrayList<>(merged.values());
    }
}
