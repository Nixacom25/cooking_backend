package com.cooked.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Web search through an official API. Search engines block scraping from our
 * server's datacenter IP (Google/DuckDuckGo answer with empty pages), so the
 * recipe web search uses Serper (Google results) or Brave Search when a key
 * is configured. Results are cached a few hours: the same dishes are searched
 * again and again, and every API call is billed.
 */
@Slf4j
@Component
public class WebSearchApiClient {

    private static final Duration CACHE_TTL = Duration.ofHours(6);
    private static final int CACHE_MAX = 1000;
    private static final int MAX_RESULTS = 10;

    private final RestTemplate restTemplate;

    @Value("${search.serper.api-key:}")
    private String serperKey;

    @Value("${search.brave.api-key:}")
    private String braveKey;

    private record Cached(List<Map<String, String>> results, Instant at) {}

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public WebSearchApiClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(4000);
        factory.setReadTimeout(6000);
        this.restTemplate = new RestTemplate(factory);
    }

    public boolean isConfigured() {
        return notBlank(serperKey) || notBlank(braveKey);
    }

    /**
     * Results as [{title, url, snippet}], or empty when no provider is
     * configured or every configured provider failed.
     */
    public List<Map<String, String>> search(String query) {
        if (!isConfigured() || query == null || query.isBlank()) return List.of();
        String key = query.trim().toLowerCase(Locale.ROOT);
        Cached hit = cache.get(key);
        if (hit != null && hit.at().plus(CACHE_TTL).isAfter(Instant.now())) return hit.results();

        List<Map<String, String>> results = List.of();
        if (notBlank(serperKey)) results = safely("Serper", () -> serper(query));
        if (results.isEmpty() && notBlank(braveKey)) results = safely("Brave", () -> brave(query));

        if (!results.isEmpty()) {
            if (cache.size() >= CACHE_MAX) cache.clear();
            cache.put(key, new Cached(results, Instant.now()));
        }
        return results;
    }

    private List<Map<String, String>> safely(String provider, java.util.concurrent.Callable<List<Map<String, String>>> call) {
        try {
            List<Map<String, String>> r = call.call();
            log.info("{} search returned {} result(s)", provider, r.size());
            return r;
        } catch (Exception e) {
            log.warn("{} search failed: {}", provider, e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, String>> serper(String query) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-KEY", serperKey.trim());
        Map<String, Object> body = Map.of("q", withRecipe(query), "num", MAX_RESULTS);
        ResponseEntity<JsonNode> res = restTemplate.exchange("https://google.serper.dev/search",
                HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
        return parseSerper(res.getBody());
    }

    private List<Map<String, String>> brave(String query) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("X-Subscription-Token", braveKey.trim());
        String url = UriComponentsBuilder.fromHttpUrl("https://api.search.brave.com/res/v1/web/search")
                .queryParam("q", withRecipe(query))
                .queryParam("count", MAX_RESULTS)
                .encode().toUriString();
        ResponseEntity<JsonNode> res = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        return parseBrave(res.getBody());
    }

    static List<Map<String, String>> parseSerper(JsonNode body) {
        List<Map<String, String>> out = new ArrayList<>();
        if (body == null) return out;
        for (JsonNode r : body.path("organic")) {
            add(out, r.path("title").asText(""), r.path("link").asText(""), r.path("snippet").asText(""));
        }
        return out;
    }

    static List<Map<String, String>> parseBrave(JsonNode body) {
        List<Map<String, String>> out = new ArrayList<>();
        if (body == null) return out;
        for (JsonNode r : body.path("web").path("results")) {
            add(out, r.path("title").asText(""), r.path("url").asText(""), r.path("description").asText(""));
        }
        return out;
    }

    private static void add(List<Map<String, String>> out, String title, String url, String snippet) {
        if (out.size() >= MAX_RESULTS || !url.startsWith("http") || title.isBlank()) return;
        out.add(Map.of("title", stripTags(title), "url", url, "snippet", stripTags(snippet)));
    }

    private static String stripTags(String s) {
        return s == null ? "" : s.replaceAll("<[^>]+>", "").replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"").trim();
    }

    private static String withRecipe(String query) {
        String q = query.trim();
        return q.toLowerCase(Locale.ROOT).contains("recipe") || q.toLowerCase(Locale.ROOT).contains("recette") ? q : q + " recipe";
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
