package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.EmailProviderStats;
import com.cooked.backend.service.EmailStatsProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brevo transactional statistics (GET /v3/smtp/statistics/aggregatedReport),
 * cached 10 minutes per (period, tag) so the backoffice never hammers the API.
 */
@Slf4j
@Service
public class BrevoEmailStatsProvider implements EmailStatsProvider {

    private static final String URL = "https://api.brevo.com/v3/smtp/statistics/aggregatedReport";
    static final Duration TTL = Duration.ofMinutes(10);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    record Cached(Instant at, Optional<EmailProviderStats> stats) {}

    public BrevoEmailStatsProvider(RestTemplate restTemplate, ObjectMapper objectMapper,
                                   @Value("${spring.mail.password:}") String apiKey) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    @Override
    public Optional<EmailProviderStats> aggregated(LocalDate from, LocalDate to, String tag) {
        if (!isConfigured()) return Optional.empty();
        String key = from + "|" + to + "|" + (tag == null ? "" : tag);
        Cached hit = cache.get(key);
        if (hit != null && hit.at().plus(TTL).isAfter(Instant.now())) return hit.stats();
        Optional<EmailProviderStats> stats = fetch(from, to, tag);
        cache.put(key, new Cached(Instant.now(), stats));
        return stats;
    }

    private Optional<EmailProviderStats> fetch(LocalDate from, LocalDate to, String tag) {
        try {
            UriComponentsBuilder uri = UriComponentsBuilder.fromHttpUrl(URL)
                    .queryParam("startDate", from.toString())
                    .queryParam("endDate", to.toString());
            if (tag != null && !tag.isBlank()) uri.queryParam("tag", tag);
            HttpHeaders headers = new HttpHeaders();
            headers.set("api-key", apiKey);
            headers.set("accept", "application/json");
            String body = restTemplate.exchange(uri.build().toUri(), HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
            return Optional.of(parse(objectMapper.readTree(body == null ? "{}" : body)));
        } catch (Exception e) {
            log.warn("Brevo statistics unavailable: {}", e.getMessage());
            return Optional.empty();
        }
    }

    static EmailProviderStats parse(JsonNode n) {
        return EmailProviderStats.builder()
                .requests(n.path("requests").asLong())
                .delivered(n.path("delivered").asLong())
                .uniqueOpens(n.path("uniqueOpens").asLong())
                .uniqueClicks(n.path("uniqueClicks").asLong())
                .hardBounces(n.path("hardBounces").asLong())
                .softBounces(n.path("softBounces").asLong())
                .blocked(n.path("blocked").asLong())
                .spamReports(n.path("spamReports").asLong())
                .unsubscribed(n.path("unsubscribed").asLong())
                .build();
    }
}
