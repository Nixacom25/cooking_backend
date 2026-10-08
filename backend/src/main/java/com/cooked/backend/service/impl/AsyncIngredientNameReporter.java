package com.cooked.backend.service.impl;

import com.cooked.backend.entity.IngredientNameSource;
import com.cooked.backend.service.IngredientNameReporter;
import com.cooked.backend.service.UnmatchedIngredientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.ArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncIngredientNameReporter implements IngredientNameReporter {

    private final UnmatchedIngredientService queue;

    @Async
    @Override
    public void report(Collection<String> names, IngredientNameSource source, String userEmail) {
        if (names == null || names.isEmpty()) return;
        try {
            queue.record(new ArrayList<>(names), source, userEmail);
        } catch (RuntimeException e) {
            log.warn("Could not record unmatched ingredient names ({}): {}", source, e.getMessage());
        }
    }
}
