package com.cooked.backend.service.impl;

import com.cooked.backend.entity.ProductEvent;
import com.cooked.backend.entity.ProductEventType;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.PaymentRequiredException;
import com.cooked.backend.service.ProductEventTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;
import java.util.function.ToIntFunction;

@Service
@RequiredArgsConstructor
public class ProductEventTrackerImpl implements ProductEventTracker {

    private final ProductEventWriter writer;

    @Override
    public <T> T track(ProductEventType type, String userEmail, String detail,
                       Supplier<T> action, ToIntFunction<T> resultCount) {
        return track(type, userEmail, detail, null, action, resultCount);
    }

    @Override
    public <T> T track(ProductEventType type, String userEmail, String detail, String target,
                       Supplier<T> action, ToIntFunction<T> resultCount) {
        long start = System.nanoTime();
        try {
            T result = action.get();
            Integer count = null;
            try {
                count = result == null ? 0 : resultCount.applyAsInt(result);
            } catch (RuntimeException ignored) {
                // a counting problem must never fail the request
            }
            writer.write(event(type, detail, true, null, start, count), userEmail);
            return result;
        } catch (RuntimeException e) {
            // Paywall refusals aren't product failures: don't count them.
            if (!(e instanceof PaymentRequiredException)) {
                ProductEvent failed = event(type, detail, false, reason(e), start, null);
                failed.setTarget(target == null || target.length() > 4000 ? null : target.trim());
                writer.write(failed, userEmail);
            }
            throw e;
        }
    }

    private static ProductEvent event(ProductEventType type, String detail, boolean success,
                                      String reason, long start, Integer count) {
        return ProductEvent.builder()
                .type(type)
                .success(success)
                .durationMs((int) ((System.nanoTime() - start) / 1_000_000))
                .resultCount(count)
                .detail(shorten(detail))
                .failureReason(shorten(reason))
                .build();
    }

    /** User-facing messages for expected failures, the exception type otherwise. */
    static String reason(RuntimeException e) {
        if (e instanceof BadRequestException && e.getMessage() != null) return e.getMessage();
        return e.getClass().getSimpleName();
    }

    static String shorten(String s) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        if (t.isEmpty()) return null;
        return t.length() <= ProductEvent.DETAIL_MAX ? t : t.substring(0, ProductEvent.DETAIL_MAX - 1) + "…";
    }
}
