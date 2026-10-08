package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.IntegrationStatusResponse;
import com.cooked.backend.dto.response.SystemStatusResponse;
import com.cooked.backend.dto.response.SystemStatusResponse.Service;
import com.cooked.backend.entity.ProductEventType;
import com.cooked.backend.repository.AutomationRunRepository;
import com.cooked.backend.repository.ProductEventRepository;
import com.cooked.backend.service.AdminIntegrationService;
import com.cooked.backend.service.IncidentService;
import com.cooked.backend.service.SystemStatusService;
import com.cooked.backend.service.monitoring.RequestMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the System health view from what the backend already knows:
 * its own request metrics, product events (scan / import), integration
 * events (webhooks, emails, AI calls), scheduled jobs, plus two HTTP probes
 * (website, image CDN, recipe API) cached for a minute.
 */
@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class SystemStatusServiceImpl implements SystemStatusService {

    static final String WEBSITE = "https://cookedapp.com";
    static final String IMAGE_CDN = "https://res.cloudinary.com";
    static final String RECIPE_API = "https://recipe.markhorsystems.com";
    private static final Duration PROBE_TTL = Duration.ofSeconds(60);

    private final RequestMetrics metrics;
    private final JdbcTemplate jdbc;
    private final ProductEventRepository events;
    private final AutomationRunRepository automationRuns;
    private final AdminIntegrationService integrations;
    private final IncidentService incidents;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private final Map<String, Probe> probes = new ConcurrentHashMap<>();

    record Probe(long at, boolean ok, int ms, String detail) {
    }

    @Override
    public SystemStatusResponse status() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime day = now.minusHours(24);
        RequestMetrics.Snapshot snap = metrics.snapshot(15);
        Integer db = dbLatency();
        Map<String, IntegrationStatusResponse> byKey = integrations.integrations().stream()
                .collect(Collectors.toMap(IntegrationStatusResponse::getKey, Function.identity(), (a, b) -> a));
        long workerFailures = automationRuns.summarySince(day).stream().mapToLong(j -> j.getFailures() == null ? 0 : j.getFailures()).sum();

        List<Service> services = new ArrayList<>();
        services.add(new Service("App API", snap.errorRate() != null && snap.errorRate() > 5 ? "DEGRADED" : "UP",
                snap.p95Ms() == null ? "Answering" : "p95 " + snap.p95Ms() + " ms"));
        services.add(new Service("Database", db == null ? "DOWN" : "UP", db == null ? "No answer" : db + " ms"));
        services.add(feature("Scan AI", ProductEventType.SCAN, day));
        services.add(integration("Recipe generation", byKey.get("MARKHOR")));
        services.add(feature("Import", ProductEventType.IMPORT, day));
        services.add(integration("RevenueCat", byKey.get("REVENUECAT")));
        services.add(integration("Push", byKey.get("FIREBASE")));
        services.add(integration("Email", byKey.get("BREVO")));
        services.add(probe("External Recipe API", RECIPE_API));
        services.add(probe("Image CDN", IMAGE_CDN));
        services.add(probe("Website", WEBSITE));
        return new SystemStatusResponse(now, snap, db, workerFailures, services, incidents.open());
    }

    Integer dbLatency() {
        try {
            long t0 = System.nanoTime();
            jdbc.queryForObject("select 1", Integer.class);
            return (int) ((System.nanoTime() - t0) / 1_000_000);
        } catch (RuntimeException e) {
            return null;
        }
    }

    Service feature(String name, ProductEventType type, LocalDateTime since) {
        long total = events.countByTypeAndCreatedAtGreaterThanEqual(type, since);
        long failed = events.countByTypeAndSuccessFalseAndCreatedAtGreaterThanEqual(type, since);
        return featureStatus(name, total, failed);
    }

    static Service featureStatus(String name, long total, long failed) {
        if (total == 0) return new Service(name, "IDLE", "No request in 24 h");
        double rate = 100.0 * (total - failed) / total;
        String status = rate < 50 ? "DOWN" : rate < 85 ? "DEGRADED" : "UP";
        return new Service(name, status, Math.round(rate) + "% success · " + total + " in 24 h");
    }

    static Service integration(String name, IntegrationStatusResponse i) {
        if (i == null) return new Service(name, "IDLE", "No data");
        if (!i.isConfigured() || "NOT_CONFIGURED".equals(i.getStatus())) return new Service(name, "NOT_CONFIGURED", "Not configured");
        if (i.getEvents24h() == 0) return new Service(name, "IDLE", "No activity in 24 h");
        long ok = i.getEvents24h() - i.getFailures24h();
        double rate = 100.0 * ok / i.getEvents24h();
        String status = rate < 50 ? "DOWN" : rate < 90 ? "DEGRADED" : "UP";
        return new Service(name, status, i.getEvents24h() + " events · " + i.getFailures24h() + " failed in 24 h");
    }

    Service probe(String name, String url) {
        Probe p = probes.compute(url, (k, old) -> old != null && System.currentTimeMillis() - old.at() < PROBE_TTL.toMillis() ? old : run(url));
        return new Service(name, p.ok() ? "UP" : "DOWN", p.detail());
    }

    private Probe run(String url) {
        long t0 = System.nanoTime();
        try {
            HttpResponse<Void> res = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
            int ms = (int) ((System.nanoTime() - t0) / 1_000_000);
            // Any HTTP answer below 500 means the service is reachable (a CDN root may answer 404).
            boolean ok = res.statusCode() < 500;
            return new Probe(System.currentTimeMillis(), ok, ms, ok ? ms + " ms" : "HTTP " + res.statusCode());
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return new Probe(System.currentTimeMillis(), false, 0, "Unreachable");
        }
    }
}
