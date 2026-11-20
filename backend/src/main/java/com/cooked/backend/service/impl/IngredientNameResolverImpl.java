package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.IngredientPayload;
import com.cooked.backend.repository.IngredientVisualRepository;
import com.cooked.backend.service.IngredientNameResolver;
import com.cooked.backend.util.IngredientKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Exact match on the normalized name, a canonical id or an alias first; then the longest catalog name found as
 * consecutive words ("ripe roma tomatoes" → Roma tomato), the last one winning a tie ("cilantro lime rice" → rice).
 * The catalog is held in memory (names only) and reloaded every few minutes.
 */
@Service
@RequiredArgsConstructor
public class IngredientNameResolverImpl implements IngredientNameResolver {

    private static final Duration TTL = Duration.ofMinutes(10);
    /** Words that are usually a unit or a second ingredient ("garlic cloves", "salt pork"): never preferred. */
    private static final Set<String> WEAK = Set.of("clove", "salt", "sprig", "stick", "sheet", "strip", "water", "ice",
            "sauce", "cheese", "salad", "sprout");
    /** One-word names that read as an adjective or a verb inside other names ("green chile", "stir-fry"). */
    private static final Set<String> SKIP = Set.of("green", "fry", "shell", "oyster", "red", "white", "black", "hot",
            "fresh", "dry", "dried", "ingredient", "generic_ingredient");
    /** Catalog rows usable as a "contained" match: a real name, not a recipe line imported as an ingredient. */
    private static final int MAX_WORDS = 4;

    private final IngredientVisualRepository visuals;

    private volatile Index index;

    record Index(Map<String, String> exact, Map<String, String> contained, Instant loadedAt) {
    }

    @Override
    public Optional<String> resolve(String rawName) {
        if (rawName == null || rawName.isBlank()) return Optional.empty();
        Index idx = index();
        String full = IngredientKeys.key(rawName);
        String head = IngredientKeys.key(rawName.split("[(,;:]", 2)[0]);
        for (String key : head.isEmpty() || head.equals(full) ? List.of(full) : List.of(head, full)) {
            String exact = idx.exact().get(key);
            if (exact != null) return Optional.of(exact);
        }
        for (String key : head.isEmpty() || head.equals(full) ? List.of(full) : List.of(head, full)) {
            String hit = closest(key, idx.contained());
            if (hit != null) return Optional.of(hit);
        }
        return Optional.empty();
    }

    @Override
    public List<IngredientPayload> keepKnown(List<IngredientPayload> detected) {
        if (detected == null) return null;
        Map<String, IngredientPayload> byName = new LinkedHashMap<>();
        for (IngredientPayload p : detected) {
            if (p == null) continue;
            resolve(p.getName()).ifPresent(name -> byName.putIfAbsent(name.toLowerCase(Locale.ROOT),
                    IngredientPayload.builder().name(name).icon(p.getIcon()).quantity(p.getQuantity()).price(p.getPrice()).build()));
        }
        return new ArrayList<>(byName.values());
    }

    private static String closest(String key, Map<String, String> contained) {
        if (key.isEmpty()) return null;
        String[] tokens = key.split("_");
        String best = null;
        int[] bestScore = {-1, -1, -1};
        for (Map.Entry<String, String> e : contained.entrySet()) {
            String[] words = e.getKey().split("_");
            int n = words.length;
            for (int end = tokens.length; end >= n; end--) {
                boolean match = true;
                for (int j = 0; j < n && match; j++) match = tokens[end - n + j].equals(words[j]);
                if (!match) continue;
                int[] score = {WEAK.contains(e.getKey()) ? 0 : 1, n, end};
                if (better(score, bestScore)) {
                    best = e.getValue();
                    bestScore = score;
                }
                break;
            }
        }
        return best;
    }

    private static boolean better(int[] a, int[] b) {
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return a[i] > b[i];
        return false;
    }

    private Index index() {
        Index i = index;
        if (i == null || i.loadedAt().plus(TTL).isBefore(Instant.now())) {
            synchronized (this) {
                i = index;
                if (i == null || i.loadedAt().plus(TTL).isBefore(Instant.now())) index = i = load();
            }
        }
        return i;
    }

    private Index load() {
        Map<String, String> exact = new HashMap<>();
        Map<String, String> contained = new HashMap<>();
        for (Object[] row : visuals.findEnabledNames()) {
            String name = (String) row[0], nameKey = (String) row[1], canonicalId = (String) row[2];
            exact.putIfAbsent(nameKey, name);
            exact.putIfAbsent(canonicalId, name);
            if (cleanName(name, nameKey)) contained.putIfAbsent(nameKey, name);
        }
        for (Object[] row : visuals.findEnabledAliases()) {
            exact.putIfAbsent((String) row[0], (String) row[1]);
        }
        exact.keySet().removeAll(Set.of("ingredient", "generic_ingredient"));
        return new Index(Map.copyOf(exact), Map.copyOf(contained), Instant.now());
    }

    /** "Roma tomatoes" yes; "Tbsp poudre de curry" or "Stir in the red onion." no. */
    static boolean cleanName(String name, String key) {
        if (key.isEmpty() || SKIP.contains(key) || key.split("_").length > MAX_WORDS) return false;
        return !name.matches(".*[0-9().:;*¼½¾⅓⅔].*")
                && !name.toLowerCase().matches("^(tbsp|tsp|cups?|c\\.|cuill|tasses?|portion|de |d').*");
    }
}
