package com.cooked.backend.service.monitoring;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory HTTP metrics of this backend instance: one bucket per minute for
 * the last hour (requests, 5xx errors, a sample of durations), plus the AI
 * requests (scan / import / generation) currently being processed.
 * Reset on restart; enough for the admin "System health" screen.
 */
@Component
public class RequestMetrics {

    static final int MINUTES = 60;
    static final int SAMPLES_PER_MINUTE = 2000;

    private final Bucket[] buckets = new Bucket[MINUTES];
    private final AtomicInteger aiInFlight = new AtomicInteger();
    private final AtomicInteger scansInFlight = new AtomicInteger();
    private final AtomicInteger importsInFlight = new AtomicInteger();
    private final long startedAt = System.currentTimeMillis();

    private static final class Bucket {
        long minute = -1;
        int count;
        int errors;
        final int[] durations = new int[SAMPLES_PER_MINUTE];
        int sampled;
    }

    /** Snapshot over the last N minutes. */
    public record Snapshot(int minutes, long requests, long errors, double requestsPerMinute, Integer p50Ms, Integer p95Ms,
                           Double errorRate, int aiInFlight, long uptimeSeconds) {
    }

    public RequestMetrics() {
        for (int i = 0; i < MINUTES; i++) buckets[i] = new Bucket();
    }

    /** True for the slow AI endpoints counted as the processing "queue". */
    public static boolean isAiRequest(String method, String path) {
        return "POST".equals(method) && path != null
                && (path.startsWith("/recipes/import") || path.startsWith("/recipes/scan") || path.startsWith("/recipes/generate"));
    }

    public void started(boolean ai) {
        started(ai, null);
    }

    public void finished(boolean ai, int status, long durationMs) {
        finished(ai, null, status, durationMs);
    }

    /** [path] tells scans and imports apart for the live counters. */
    public void started(boolean ai, String path) {
        if (!ai) return;
        aiInFlight.incrementAndGet();
        AtomicInteger kind = kindCounter(path);
        if (kind != null) kind.incrementAndGet();
    }

    public void finished(boolean ai, String path, int status, long durationMs) {
        if (ai) {
            aiInFlight.decrementAndGet();
            AtomicInteger kind = kindCounter(path);
            if (kind != null) kind.decrementAndGet();
        }
        record(System.currentTimeMillis() / 60_000, status, durationMs);
    }

    private AtomicInteger kindCounter(String path) {
        if (path == null) return null;
        if (path.startsWith("/recipes/scan")) return scansInFlight;
        if (path.startsWith("/recipes/import")) return importsInFlight;
        return null;
    }

    public int scansInFlight() {
        return Math.max(0, scansInFlight.get());
    }

    public int importsInFlight() {
        return Math.max(0, importsInFlight.get());
    }

    synchronized void record(long minute, int status, long durationMs) {
        Bucket b = buckets[(int) (minute % MINUTES)];
        if (b.minute != minute) {
            b.minute = minute;
            b.count = 0;
            b.errors = 0;
            b.sampled = 0;
        }
        b.count++;
        if (status >= 500) b.errors++;
        if (b.sampled < SAMPLES_PER_MINUTE) b.durations[b.sampled++] = (int) Math.min(durationMs, Integer.MAX_VALUE);
    }

    public Snapshot snapshot(int minutes) {
        return snapshotAt(System.currentTimeMillis() / 60_000, minutes);
    }

    synchronized Snapshot snapshotAt(long nowMinute, int minutes) {
        int span = Math.max(1, Math.min(minutes, MINUTES));
        long requests = 0;
        long errors = 0;
        List<Integer> all = new ArrayList<>();
        for (Bucket b : buckets) {
            if (b.minute < 0 || b.minute <= nowMinute - span || b.minute > nowMinute) continue;
            requests += b.count;
            errors += b.errors;
            for (int i = 0; i < b.sampled; i++) all.add(b.durations[i]);
        }
        int[] sorted = all.stream().mapToInt(Integer::intValue).toArray();
        Arrays.sort(sorted);
        return new Snapshot(span, requests, errors, Math.round(10.0 * requests / span) / 10.0,
                percentile(sorted, 50), percentile(sorted, 95),
                requests == 0 ? null : Math.round(10000.0 * errors / requests) / 100.0,
                Math.max(0, aiInFlight.get()), (System.currentTimeMillis() - startedAt) / 1000);
    }

    /** One minute of the series (oldest first). */
    public record Minute(long minute, int requests, int errors, Integer p50Ms, Integer p95Ms) {
    }

    /** Last [minutes] minutes, one point per minute (empty minutes included). */
    public List<Minute> series(int minutes) {
        return seriesAt(System.currentTimeMillis() / 60_000, minutes);
    }

    synchronized List<Minute> seriesAt(long nowMinute, int minutes) {
        int span = Math.max(1, Math.min(minutes, MINUTES));
        List<Minute> out = new ArrayList<>(span);
        for (long m = nowMinute - span + 1; m <= nowMinute; m++) {
            Bucket b = buckets[(int) (m % MINUTES)];
            if (b.minute != m) {
                out.add(new Minute(m, 0, 0, null, null));
                continue;
            }
            int[] d = Arrays.copyOf(b.durations, b.sampled);
            Arrays.sort(d);
            out.add(new Minute(m, b.count, b.errors, percentile(d, 50), percentile(d, 95)));
        }
        return out;
    }

    static Integer percentile(int[] sorted, int p) {
        if (sorted.length == 0) return null;
        int idx = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(idx, sorted.length - 1))];
    }
}
