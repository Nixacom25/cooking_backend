package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.OnboardingStepRequest;
import com.cooked.backend.dto.response.OnboardingFunnelResponse;
import com.cooked.backend.entity.OnboardingStepView;
import com.cooked.backend.repository.OnboardingStepViewRepository;
import com.cooked.backend.service.OnboardingFunnelService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class OnboardingFunnelServiceImpl implements OnboardingFunnelService {

    /** Last onboarding page of the app ("Perfect meal") when no data says otherwise. */
    static final int DEFAULT_LAST_STEP = 29;
    /** Per install, per 10-minute window (an onboarding has about 30 screens). */
    static final int VIEWS_PER_WINDOW = 120;
    private static final long WINDOW_MS = 10 * 60_000L;
    private static final Set<String> PLATFORMS = Set.of("ios", "android");

    private final OnboardingStepViewRepository views;
    private final Map<String, long[]> windows = new ConcurrentHashMap<>();   // install -> [window start, count]

    @Override
    public void record(OnboardingStepRequest r, String platform, String appVersion) {
        String install = AppEventServiceImpl.hash(r.installId());
        if (!allow(install) || views.existsByInstallIdAndStep(install, r.step())) return;
        try {
            views.save(OnboardingStepView.builder()
                    .installId(install)
                    .step(r.step())
                    .screen(r.screen().trim())
                    .platform(platformOf(platform))
                    .appVersion(appVersion == null || appVersion.isBlank() ? null : appVersion.trim().substring(0, Math.min(32, appVersion.trim().length())))
                    .build());
        } catch (DataIntegrityViolationException duplicate) {
            // the same screen sent twice at once: already counted
        }
    }

    @Override
    @Transactional(readOnly = true)
    public OnboardingFunnelResponse funnel(LocalDate from, LocalDate to, String platform) {
        String p = platform == null || !PLATFORMS.contains(platform.toLowerCase(Locale.ROOT)) ? "" : platform.toLowerCase(Locale.ROOT);
        var start = from.atStartOfDay();
        var end = to.plusDays(1).atStartOfDay();

        Map<Integer, String> names = new TreeMap<>();
        views.screenNames().forEach(n -> names.put(n.getStep(), n.getScreen()));
        int lastStep = names.isEmpty() ? DEFAULT_LAST_STEP : Math.max(DEFAULT_LAST_STEP, Collections.max(names.keySet()));

        Map<Integer, Long> reached = new HashMap<>();
        views.reachedByStep(start, end, p).forEach(c -> reached.put(c.getStep(), c.getUsers()));
        Map<Integer, Long> left = new HashMap<>();
        views.leftByStep(start, end, p).forEach(c -> left.put(c.getStep(), c.getUsers()));

        List<OnboardingFunnelResponse.Step> steps = new ArrayList<>();
        for (int i = 0; i <= lastStep; i++) {
            steps.add(new OnboardingFunnelResponse.Step(i, names.getOrDefault(i, "Screen " + (i + 1)),
                    reached.getOrDefault(i, 0L), left.getOrDefault(i, 0L)));
        }
        long started = reached.getOrDefault(0, 0L);
        long finished = reached.getOrDefault(lastStep, 0L);
        return new OnboardingFunnelResponse(from, to, p.isEmpty() ? "all" : p, started, finished,
                median(views.finishTimes(start, end, p, lastStep)), lastStep, steps);
    }

    static Long median(List<OnboardingStepViewRepository.Duration> times) {
        long[] secs = times.stream()
                .filter(t -> t.getStartedAt() != null && t.getFinishedAt() != null)
                .mapToLong(t -> Math.max(0, Duration.between(t.getStartedAt(), t.getFinishedAt()).getSeconds()))
                .sorted().toArray();
        if (secs.length == 0) return null;
        int m = secs.length / 2;
        return secs.length % 2 == 1 ? secs[m] : (secs[m - 1] + secs[m]) / 2;
    }

    static String platformOf(String raw) {
        String p = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return PLATFORMS.contains(p) ? p : "other";
    }

    synchronized boolean allow(String key) {
        long now = System.currentTimeMillis();
        long[] w = windows.computeIfAbsent(key, k -> new long[]{now, 0});
        if (now - w[0] > WINDOW_MS) {
            w[0] = now;
            w[1] = 0;
        }
        if (windows.size() > 50_000) windows.entrySet().removeIf(en -> now - en.getValue()[0] > WINDOW_MS);
        return ++w[1] <= VIEWS_PER_WINDOW;
    }
}
