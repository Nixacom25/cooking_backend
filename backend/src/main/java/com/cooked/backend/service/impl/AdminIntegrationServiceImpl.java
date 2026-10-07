package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.EmailSummaryResponse;
import com.cooked.backend.dto.response.IntegrationDetailResponse;
import com.cooked.backend.dto.response.IntegrationStatusResponse;
import com.cooked.backend.entity.EmailTemplate;
import com.cooked.backend.entity.IntegrationEvent;
import com.cooked.backend.entity.IntegrationKey;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.IntegrationEventRepository;
import com.cooked.backend.repository.IntegrationEventRepository.KeySummary;
import com.cooked.backend.repository.ProductEventRepository;
import com.cooked.backend.service.AdminIntegrationService;
import com.cooked.backend.service.IntegrationConfigProbe;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Integrations are read from integration_events (webhooks, emails, billing syncs)
 * and, for the AI service, from product_events (scan / import / search calls).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminIntegrationServiceImpl implements AdminIntegrationService {

    /** Above this share of failures in 24h an integration is flagged. */
    static final double WARNING_FAILURE_RATE = 0.2;

    private final IntegrationEventRepository events;
    private final ProductEventRepository productEvents;
    private final IntegrationConfigProbe probe;
    private final com.cooked.backend.service.EmailStatsProvider emailStats;

    @Override
    public List<IntegrationStatusResponse> integrations() {
        return integrationsAt(LocalDateTime.now());
    }

    List<IntegrationStatusResponse> integrationsAt(LocalDateTime now) {
        Map<IntegrationKey, KeySummary> day = byKey(events.summarySince(now.minusHours(24)));
        Map<IntegrationKey, KeySummary> month = byKey(events.summarySince(now.minusDays(30)));
        Counts ai24 = aiCounts(now.minusHours(24));
        Counts ai30 = aiCounts(now.minusDays(30));

        List<IntegrationStatusResponse> out = new ArrayList<>();
        for (IntegrationKey key : IntegrationKey.values()) {
            Counts d = key == IntegrationKey.MARKHOR ? ai24 : Counts.of(day.get(key));
            Counts m = key == IntegrationKey.MARKHOR ? ai30 : Counts.of(month.get(key));
            LocalDateTime last = key == IntegrationKey.MARKHOR ? null : month.containsKey(key) ? month.get(key).getLastAt() : null;
            out.add(status(key, probe.isConfigured(key), d, m, last));
        }
        return out;
    }

    @Override
    public IntegrationDetailResponse integration(String key, int page, int size) {
        IntegrationKey k = parse(key);
        LocalDateTime now = LocalDateTime.now();
        IntegrationStatusResponse summary = integrationsAt(now).stream().filter(s -> s.getKey().equals(k.name())).findFirst().orElseThrow();
        int p = Math.max(0, page);
        int s = Math.max(1, Math.min(size, MAX_PAGE_SIZE));

        if (k == IntegrationKey.MARKHOR) {
            List<IntegrationDetailResponse.NameStats> byType = productEvents.summaryByType(now.minusDays(30)).stream()
                    .map(t -> new IntegrationDetailResponse.NameStats(t.getType().name(), nz(t.getTotal()), nz(t.getTotal()) - nz(t.getSuccesses()), null))
                    .toList();
            Counts c7 = aiCounts(now.minusDays(7));
            Counts p7 = aiCounts(now.minusDays(14)).minus(c7);
            return IntegrationDetailResponse.builder().summary(summary).byName(byType).events(List.of())
                    .successRate7d(c7.successRate()).successRatePrev7d(p7.successRate()).page(0).totalPages(0).build();
        }

        List<IntegrationDetailResponse.NameStats> byName = events.byName(k, now.minusDays(30)).stream()
                .map(n -> new IntegrationDetailResponse.NameStats(n.getName(), nz(n.getTotal()), nz(n.getFailures()), n.getLastAt()))
                .toList();
        Counts c7 = Counts.of(byKey(events.summarySince(now.minusDays(7))).get(k));
        Counts p7 = Counts.of(byKey(events.summarySince(now.minusDays(14))).get(k)).minus(c7);
        Page<IntegrationEvent> rows = events.findByIntegrationOrderByCreatedAtDesc(k, PageRequest.of(p, s));
        OptionalDouble latency = rows.getContent().stream().filter(e -> e.getLatencyMs() != null).mapToInt(IntegrationEvent::getLatencyMs).average();

        return IntegrationDetailResponse.builder()
                .summary(summary)
                .successRate7d(c7.successRate())
                .successRatePrev7d(p7.successRate())
                .avgLatencyMs7d(latency.isPresent() ? Math.round(latency.getAsDouble() * 10) / 10.0 : null)
                .byName(byName)
                .events(rows.getContent().stream().map(e -> IntegrationDetailResponse.Event.builder()
                        .id(e.getId()).name(e.getName()).success(e.isSuccess()).httpStatus(e.getHttpStatus())
                        .latencyMs(e.getLatencyMs()).detail(e.getDetail()).createdAt(e.getCreatedAt()).build()).toList())
                .totalEvents(rows.getTotalElements())
                .page(p)
                .totalPages(rows.getTotalPages())
                .build();
    }

    @Override
    public EmailSummaryResponse emails(int days) {
        int d = Math.max(1, Math.min(days, 90));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = now.minusDays(d);
        Counts cur = Counts.of(byKey(events.summarySince(from)).get(IntegrationKey.BREVO));
        Counts prev = Counts.of(byKey(events.summarySince(from.minusDays(d))).get(IntegrationKey.BREVO)).minus(cur);

        java.time.LocalDate today = now.toLocalDate();
        Map<String, IntegrationEventRepository.NameSummary> byName = new HashMap<>();
        events.byName(IntegrationKey.BREVO, from).forEach(n -> byName.put(n.getName(), n));
        List<EmailSummaryResponse.TemplateStats> templates = Arrays.stream(EmailTemplate.values()).map(t -> {
            IntegrationEventRepository.NameSummary n = byName.get(t.name());
            return EmailSummaryResponse.TemplateStats.builder()
                    .template(t.name()).label(t.getLabel()).trigger(t.getTrigger()).automation(t.isAutomation())
                    .sent(n == null ? 0 : nz(n.getTotal())).failed(n == null ? 0 : nz(n.getFailures()))
                    .lastSentAt(n == null ? null : n.getLastAt())
                    .providerStats(t.isAutomation()
                            ? emailStats.aggregated(today.minusDays(d - 1L), today, t.name().toLowerCase(Locale.ROOT)).orElse(null) : null)
                    .build();
        }).sorted(Comparator.comparingLong(EmailSummaryResponse.TemplateStats::getSent).reversed()).toList();

        return EmailSummaryResponse.builder()
                .days(d)
                .provider(IntegrationKey.BREVO.getLabel())
                .configured(probe.isConfigured(IntegrationKey.BREVO))
                .sent(cur.total).sentPrev(prev.total).failed(cur.failures)
                .acceptedRate(cur.successRate()).acceptedRatePrev(prev.successRate())
                .trackingSince(events.firstAt(IntegrationKey.BREVO))
                .templates(templates)
                .providerStats(emailStats.aggregated(today.minusDays(d - 1L), today, null).orElse(null))
                .providerStatsPrev(emailStats.aggregated(today.minusDays(2L * d - 1), today.minusDays(d), null).orElse(null))
                .build();
    }

    // ---- helpers -----------------------------------------------------------

    static IntegrationStatusResponse status(IntegrationKey key, boolean configured, Counts day, Counts month, LocalDateTime last) {
        String status;
        if (!configured) status = "NOT_CONFIGURED";
        else if (day.total > 0 && (double) day.failures / day.total > WARNING_FAILURE_RATE) status = "WARNING";
        else if (month.total == 0) status = "IDLE";
        else status = "CONNECTED";
        return IntegrationStatusResponse.builder()
                .key(key.name()).name(key.getLabel()).description(key.getDescription())
                .configured(configured).status(status).lastEventAt(last)
                .events24h(day.total).failures24h(day.failures).events30d(month.total).failures30d(month.failures)
                .activity(activity(key))
                .build();
    }

    static String activity(IntegrationKey key) {
        return switch (key) {
            case REVENUECAT, STRIPE, APPLE, GOOGLE_PLAY -> "webhooks received";
            case BREVO -> "emails sent";
            case OPENAI -> "billing syncs";
            case MARKHOR -> "scan / import / search calls";
            case FIREBASE, CLOUDINARY -> "not tracked yet";
        };
    }

    private Counts aiCounts(LocalDateTime from) {
        long total = 0, ok = 0;
        for (ProductEventRepository.TypeSummary t : productEvents.summaryByType(from)) {
            total += nz(t.getTotal());
            ok += nz(t.getSuccesses());
        }
        return new Counts(total, total - ok);
    }

    private static Map<IntegrationKey, KeySummary> byKey(List<KeySummary> rows) {
        Map<IntegrationKey, KeySummary> m = new EnumMap<>(IntegrationKey.class);
        rows.forEach(r -> m.put(r.getIntegration(), r));
        return m;
    }

    static IntegrationKey parse(String key) {
        try {
            return IntegrationKey.valueOf(key.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (RuntimeException e) {
            throw new ResourceNotFoundException("Unknown integration " + key);
        }
    }

    private static long nz(Long v) {
        return v == null ? 0 : v;
    }

    /** Totals and failures of a window. */
    record Counts(long total, long failures) {
        static Counts of(KeySummary s) {
            return s == null ? new Counts(0, 0) : new Counts(nz(s.getTotal()), nz(s.getFailures()));
        }

        Counts minus(Counts o) {
            return new Counts(Math.max(0, total - o.total), Math.max(0, failures - o.failures));
        }

        Double successRate() {
            return total == 0 ? null : Math.round(1000.0 * (total - failures) / total) / 10.0;
        }
    }
}
