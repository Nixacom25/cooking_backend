package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.AppEventsRequest;
import com.cooked.backend.dto.request.SiteVisitRequest;
import com.cooked.backend.entity.ProductEvent;
import com.cooked.backend.entity.ProductEventType;
import com.cooked.backend.entity.SiteVisit;
import com.cooked.backend.repository.SiteVisitRepository;
import com.cooked.backend.service.AppEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AppEventServiceImpl implements AppEventService {

    /** Per user (app) or visitor (site), per 10-minute window. */
    static final int APP_EVENTS_PER_WINDOW = 300;
    static final int SITE_VIEWS_PER_WINDOW = 120;
    private static final long WINDOW_MS = 10 * 60_000L;

    private final ProductEventWriter writer;
    private final SiteVisitRepository visits;
    private final Map<String, long[]> windows = new ConcurrentHashMap<>();   // key -> [window start, count]

    @Override
    public int record(AppEventsRequest request, String userEmail) {
        int kept = 0;
        for (AppEventsRequest.Event e : request.events()) {
            ProductEventType type = typeOf(e.type());
            if (type == null || !type.fromApp()) continue;
            if (!allow("app:" + userEmail, APP_EVENTS_PER_WINDOW)) break;
            writer.write(ProductEvent.builder()
                    .type(type)
                    .success(true)
                    .durationMs(e.durationMs())
                    .detail(ProductEventTrackerImpl.shorten(e.detail()))
                    .build(), userEmail);
            kept++;
        }
        return kept;
    }

    @Override
    public void visit(SiteVisitRequest r) {
        String visitor = hash(r.visitor());
        if (!allow("site:" + visitor, SITE_VIEWS_PER_WINDOW)) return;
        visits.save(SiteVisit.builder()
                .day(LocalDate.now())
                .visitor(visitor)
                .path(r.path().length() > 200 ? r.path().substring(0, 200) : r.path())
                .referrer(referrerHost(r.referrer()))
                .build());
    }

    static ProductEventType typeOf(String raw) {
        if (raw == null) return null;
        try {
            return ProductEventType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Host only, lower case, without "www." (null for empty or own site). */
    static String referrerHost(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String h = raw.trim().toLowerCase(Locale.ROOT).replaceFirst("^https?://", "").replaceFirst("/.*$", "").replaceFirst("^www\\.", "");
        if (h.isEmpty() || h.endsWith("cookedapp.com") || !h.matches("[a-z0-9.-]{1,120}")) return null;
        return h;
    }

    synchronized boolean allow(String key, int max) {
        long now = System.currentTimeMillis();
        long[] w = windows.computeIfAbsent(key, k -> new long[]{now, 0});
        if (now - w[0] > WINDOW_MS) {
            w[0] = now;
            w[1] = 0;
        }
        if (windows.size() > 50_000) windows.entrySet().removeIf(en -> now - en.getValue()[0] > WINDOW_MS);
        return ++w[1] <= max;
    }

    static String hash(String visitor) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(visitor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
