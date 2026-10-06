package com.cooked.backend.service;

import com.cooked.backend.entity.ProductEventType;

import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * Measures a product action (outcome, duration, result count) and records it
 * for the admin analytics, without changing the action's behaviour: the
 * result is returned and any exception is rethrown unchanged.
 */
public interface ProductEventTracker {

    /**
     * @param userEmail   authenticated user (may be null)
     * @param detail      import domain, search query… (trimmed / shortened by the tracker)
     * @param resultCount how many results the action returned (null-safe)
     */
    <T> T track(ProductEventType type, String userEmail, String detail,
                Supplier<T> action, ToIntFunction<T> resultCount);
}
