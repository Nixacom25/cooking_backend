package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.UnmatchedIngredientFilter;
import com.cooked.backend.dto.response.CatalogSeedResponse;
import com.cooked.backend.dto.response.UnmatchedIngredientResponse;
import com.cooked.backend.dto.response.UnmatchedQueueResponse;
import com.cooked.backend.entity.*;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.IngredientVisualRepository;
import com.cooked.backend.repository.RecipeIngredientRepository;
import com.cooked.backend.repository.UnmatchedIngredientRepository;
import com.cooked.backend.repository.UnmatchedIngredientUserRepository;
import com.cooked.backend.repository.spec.IngredientCatalogSpecs;
import com.cooked.backend.service.IngredientCatalogService;
import com.cooked.backend.service.UnmatchedIngredientService;
import com.cooked.backend.util.IngredientKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UnmatchedIngredientServiceImpl implements UnmatchedIngredientService {

    /** Below this similarity the queue shows no suggested match. */
    static final double MIN_SUGGESTION = 0.6;
    static final int MAX_BACKFILL = 2000;

    private final UnmatchedIngredientRepository unmatched;
    private final UnmatchedIngredientUserRepository unmatchedUsers;
    private final IngredientVisualRepository visuals;
    private final RecipeIngredientRepository recipeIngredients;
    private final IngredientCatalogService catalog;
    private final PlatformTransactionManager txManager;

    @Override
    public int record(Collection<String> names, IngredientNameSource source, String userEmail) {
        if (names == null || source == null) return 0;
        Map<String, String> byKey = new LinkedHashMap<>();
        for (String n : names) {
            if (n == null) continue;
            String name = n.trim().replaceAll("\\s+", " ");
            String key = IngredientKeys.key(name);
            if (name.length() > IngredientKeys.MAX_LENGTH || key.length() < 2 || key.matches("[0-9_]+")) continue;
            byKey.putIfAbsent(key, name);
        }
        if (byKey.isEmpty()) return 0;
        Set<String> known = new HashSet<>();
        visuals.findByAnyKey(byKey.keySet()).forEach(v -> known.addAll(IngredientCatalogServiceImpl.keysOf(v)));
        TransactionTemplate tx = new TransactionTemplate(txManager);
        int recorded = 0;
        for (Map.Entry<String, String> e : byKey.entrySet()) {
            if (known.contains(e.getKey())) continue;
            try {
                tx.executeWithoutResult(s -> recordOne(e.getKey(), e.getValue(), source, userEmail));
            } catch (DataIntegrityViolationException race) {
                // another request created the same name at the same moment: count on the row it created
                tx.executeWithoutResult(s -> recordOne(e.getKey(), e.getValue(), source, userEmail));
            }
            recorded++;
        }
        return recorded;
    }

    private void recordOne(String key, String sample, IngredientNameSource source, String userEmail) {
        LocalDateTime now = LocalDateTime.now();
        UnmatchedIngredient u = unmatched.findByNameKey(key).orElseGet(() -> UnmatchedIngredient.builder()
                .nameKey(key).sampleName(sample).firstSeenAt(now).lastSeenAt(now).build());
        if (u.getStatus() == UnmatchedStatus.RESOLVED) {
            // the visual or alias that resolved it is gone: back in the queue
            u.setStatus(UnmatchedStatus.OPEN);
            u.setResolvedVisualId(null);
            u.setResolvedAt(null);
            u.setResolvedBy(null);
        }
        u.setSampleName(sample);
        u.setLastSeenAt(now);
        u.count(source);
        u = unmatched.saveAndFlush(u);
        if (userEmail != null && !unmatchedUsers.existsByUnmatchedIdAndUserEmail(u.getId(), userEmail)) {
            unmatchedUsers.saveAndFlush(UnmatchedIngredientUser.builder().unmatchedId(u.getId()).userEmail(userEmail).build());
            u.setUserCount(u.getUserCount() + 1);
            unmatched.save(u);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UnmatchedQueueResponse queue(UnmatchedIngredientFilter f, String sort, int page, int size) {
        int p = Math.max(0, page), s = Math.min(Math.max(1, size), 100);
        Page<UnmatchedIngredient> rows = unmatched.findAll(IngredientCatalogSpecs.unmatched(f), PageRequest.of(p, s, sort(sort)));
        Matcher matcher = new Matcher(visuals);
        LocalDateTime d30 = LocalDateTime.now().minusDays(30);
        return new UnmatchedQueueResponse(rows.getContent().stream().map(u -> toResponse(u, matcher)).toList(),
                rows.getTotalElements(), p, s, rows.getTotalPages(),
                unmatched.countByStatus(UnmatchedStatus.OPEN),
                unmatched.countByStatusAndLastSeenAtAfter(UnmatchedStatus.OPEN, d30),
                unmatched.countByStatusAndFirstSeenAtAfter(UnmatchedStatus.OPEN, LocalDateTime.now().minusDays(7)),
                unmatched.sumSeen(UnmatchedStatus.OPEN, d30),
                unmatched.countByStatus(UnmatchedStatus.IGNORED),
                unmatched.countByStatus(UnmatchedStatus.RESOLVED));
    }

    private static Sort sort(String sort) {
        if ("users".equalsIgnoreCase(sort)) return Sort.by("userCount").descending().and(Sort.by("seenCount").descending());
        if ("recent".equalsIgnoreCase(sort)) return Sort.by("lastSeenAt").descending();
        if ("new".equalsIgnoreCase(sort)) return Sort.by("firstSeenAt").descending();
        if ("name".equalsIgnoreCase(sort)) return Sort.by("nameKey").ascending();
        return Sort.by("seenCount").descending().and(Sort.by("lastSeenAt").descending());
    }

    @Override
    @Transactional
    public UnmatchedIngredientResponse addAsAlias(UUID id, UUID visualId, String adminEmail) {
        UnmatchedIngredient u = find(id);
        if (u.getStatus() == UnmatchedStatus.RESOLVED) throw new BadRequestException("This name is already resolved.");
        // adding the alias closes every open name with the same key, this one included
        catalog.addAlias(visualId, u.getSampleName(), adminEmail);
        UnmatchedIngredient after = find(id);
        if (after.getStatus() != UnmatchedStatus.RESOLVED) {
            after.setStatus(UnmatchedStatus.RESOLVED);
            after.setResolvedVisualId(visualId);
            after.setResolvedAt(LocalDateTime.now());
            after.setResolvedBy(adminEmail);
            unmatched.save(after);
        }
        return toResponse(after, null);
    }

    @Override
    @Transactional
    public UnmatchedIngredientResponse ignore(UUID id, String adminEmail) {
        UnmatchedIngredient u = find(id);
        u.setStatus(UnmatchedStatus.IGNORED);
        u.setResolvedAt(LocalDateTime.now());
        u.setResolvedBy(adminEmail);
        return toResponse(unmatched.save(u), null);
    }

    @Override
    @Transactional
    public UnmatchedIngredientResponse reopen(UUID id) {
        UnmatchedIngredient u = find(id);
        u.setStatus(UnmatchedStatus.OPEN);
        u.setResolvedVisualId(null);
        u.setResolvedAt(null);
        u.setResolvedBy(null);
        return toResponse(unmatched.save(u), null);
    }

    @Override
    @Transactional
    public CatalogSeedResponse backfillFromRecipes(int limit) {
        int n = Math.min(Math.max(1, limit), MAX_BACKFILL);
        Map<String, Object[]> byKey = new LinkedHashMap<>();
        for (Object[] row : recipeIngredients.mostUsedNames(PageRequest.of(0, n))) {
            String name = row[0] == null ? "" : row[0].toString().trim().replaceAll("\\s+", " ");
            String key = IngredientKeys.key(name);
            if (name.length() > IngredientKeys.MAX_LENGTH || key.length() < 2 || key.matches("[0-9_]+")) continue;
            Object[] prev = byKey.get(key);
            long recipes = ((Number) row[1]).longValue();
            if (prev == null) byKey.put(key, new Object[]{name, recipes});
            else prev[1] = (Long) prev[1] + recipes;
        }
        Set<String> known = new HashSet<>();
        List<String> keys = new ArrayList<>(byKey.keySet());
        for (int i = 0; i < keys.size(); i += 500) {
            visuals.findByAnyKey(keys.subList(i, Math.min(keys.size(), i + 500)))
                    .forEach(v -> known.addAll(IngredientCatalogServiceImpl.keysOf(v)));
        }
        int created = 0, skipped = 0;
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, Object[]> e : byKey.entrySet()) {
            if (known.contains(e.getKey()) || unmatched.findByNameKey(e.getKey()).isPresent()) {
                skipped++;
                continue;
            }
            long recipes = (Long) e.getValue()[1];
            unmatched.save(UnmatchedIngredient.builder().nameKey(e.getKey()).sampleName((String) e.getValue()[0])
                    .seenCount(recipes).importCount(recipes).firstSeenAt(now).lastSeenAt(now).build());
            created++;
        }
        return new CatalogSeedResponse(created, skipped);
    }

    private UnmatchedIngredient find(UUID id) {
        return unmatched.findById(id).orElseThrow(() -> new ResourceNotFoundException("Name not found in the queue"));
    }

    static UnmatchedIngredientResponse toResponse(UnmatchedIngredient u, Matcher matcher) {
        UnmatchedIngredientResponse.Suggestion suggestion = matcher == null || u.getStatus() != UnmatchedStatus.OPEN
                ? null : matcher.best(u.getNameKey());
        return new UnmatchedIngredientResponse(u.getId(), u.getSampleName(), u.getNameKey(), u.getSeenCount(),
                u.getUserCount(), u.mainSource(), u.getScanCount(), u.getImportCount(), u.getGroceryCount(),
                u.getFirstSeenAt(), u.getLastSeenAt(), u.getStatus(), suggestion, u.getResolvedVisualId());
    }

    /** Closest enabled visual for a key, over names, canonical ids and aliases. */
    static final class Matcher {

        private final Map<UUID, Object[]> visualsById = new HashMap<>();
        private final List<Object[]> keys = new ArrayList<>();

        Matcher(IngredientVisualRepository repo) {
            for (Object[] r : repo.nameKeys()) {
                visualsById.put((UUID) r[0], r);
                keys.add(new Object[]{r[0], r[1]});
                keys.add(new Object[]{r[0], r[2]});
            }
            keys.addAll(repo.aliasKeys());
        }

        UnmatchedIngredientResponse.Suggestion best(String key) {
            UUID bestId = null;
            double best = 0;
            for (Object[] k : keys) {
                double score = IngredientKeys.similarity(key, (String) k[1]);
                if (score > best) {
                    best = score;
                    bestId = (UUID) k[0];
                }
            }
            if (bestId == null || best < MIN_SUGGESTION) return null;
            Object[] v = visualsById.get(bestId);
            return new UnmatchedIngredientResponse.Suggestion(bestId, (String) v[2], (String) v[3], Math.round(best * 100) / 100.0);
        }
    }
}
