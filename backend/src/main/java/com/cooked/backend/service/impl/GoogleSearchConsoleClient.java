package com.cooked.backend.service.impl;

import com.cooked.backend.service.SearchConsoleClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Search Console API with a service account (GOOGLE_SEARCH_CONSOLE_CREDENTIALS,
 * base64 JSON, added as a user of the property) for SEARCH_CONSOLE_SITE
 * ("sc-domain:cookedapp.com" or "https://cookedapp.com/"). Cached 1 hour.
 */
@Slf4j
@Service
public class GoogleSearchConsoleClient implements SearchConsoleClient {

    private static final String SCOPE = "https://www.googleapis.com/auth/webmasters.readonly";
    static final Duration TTL = Duration.ofHours(1);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String site;
    private final GoogleCredentials credentials;
    private final Map<String, CachedRows> cache = new ConcurrentHashMap<>();

    record CachedRows(Instant at, List<Row> rows) {}

    public GoogleSearchConsoleClient(RestTemplate restTemplate, ObjectMapper objectMapper,
                                     @Value("${search-console.site:}") String site,
                                     @Value("${search-console.credentials:}") String credentialsBase64) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.site = site == null ? "" : site.trim();
        this.credentials = load(credentialsBase64);
    }

    private static GoogleCredentials load(String b64) {
        if (b64 == null || b64.isBlank()) return null;
        try {
            return GoogleCredentials.fromStream(new ByteArrayInputStream(Base64.getDecoder().decode(b64.trim()))).createScoped(List.of(SCOPE));
        } catch (Exception e) {
            log.warn("Search Console credentials unreadable: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public boolean isConfigured() {
        return credentials != null && !site.isEmpty();
    }

    @Override
    public String siteUrl() {
        return site;
    }

    @Override
    public Optional<Row> totals(LocalDate from, LocalDate to) {
        List<Row> rows = query(from, to, null, 1);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<Row> byPage(LocalDate from, LocalDate to, int limit) {
        return query(from, to, "page", limit);
    }

    private List<Row> query(LocalDate from, LocalDate to, String dimension, int limit) {
        if (!isConfigured()) return List.of();
        String key = from + "|" + to + "|" + dimension + "|" + limit;
        CachedRows hit = cache.get(key);
        if (hit != null && hit.at().plus(TTL).isAfter(Instant.now())) return hit.rows();
        List<Row> rows;
        try {
            credentials.refreshIfExpired();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(credentials.getAccessToken().getTokenValue());
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("startDate", from.toString());
            body.put("endDate", to.toString());
            body.put("rowLimit", limit);
            if (dimension != null) body.put("dimensions", List.of(dimension));
            String url = "https://www.googleapis.com/webmasters/v3/sites/" + UriUtils.encodePathSegment(site, StandardCharsets.UTF_8) + "/searchAnalytics/query";
            String json = restTemplate.postForObject(java.net.URI.create(url), new HttpEntity<>(body, headers), String.class);
            rows = parse(objectMapper.readTree(json == null ? "{}" : json));
        } catch (Exception e) {
            log.warn("Search Console unavailable: {}", e.getMessage());
            rows = List.of();
        }
        cache.put(key, new CachedRows(Instant.now(), rows));
        return rows;
    }

    static List<Row> parse(JsonNode root) {
        List<Row> out = new ArrayList<>();
        for (JsonNode r : root.path("rows")) {
            String k = r.path("keys").isArray() && r.path("keys").size() > 0 ? r.path("keys").get(0).asText() : "";
            out.add(new Row(k, r.path("clicks").asDouble(), r.path("impressions").asDouble(),
                    Math.round(r.path("ctr").asDouble() * 1000) / 10.0, Math.round(r.path("position").asDouble() * 10) / 10.0));
        }
        return out;
    }
}
