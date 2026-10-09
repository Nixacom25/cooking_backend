package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.IngredientVisualCreateRequest;
import com.cooked.backend.dto.request.IngredientVisualFilter;
import com.cooked.backend.dto.request.IngredientVisualUpdateRequest;
import com.cooked.backend.dto.response.*;
import com.cooked.backend.entity.*;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.IngredientCatalogReleaseRepository;
import com.cooked.backend.repository.IngredientRepository;
import com.cooked.backend.repository.IngredientVisualRepository;
import com.cooked.backend.repository.UnmatchedIngredientRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.repository.spec.IngredientCatalogSpecs;
import com.cooked.backend.service.IngredientCatalogService;
import com.cooked.backend.util.IngredientCategories;
import com.cooked.backend.util.IngredientKeys;
import com.cooked.backend.util.SvgAssets;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IngredientCatalogServiceImpl implements IngredientCatalogService {

    public static final List<String> PRESETS = List.of("produceFloat", "leafFlutter", "herbSway", "grainScatter",
            "spiceSprinkle", "liquidPour", "pourOrDrip", "dairyWobble", "cheeseStretch", "proteinBounce", "fishSwim",
            "breadRise", "fruitPop", "steam", "sparkle");

    /** Default categories and the preset each one suggests in the Add drawer. */
    static final Map<String, String> CATEGORY_PRESETS = new LinkedHashMap<>();

    static {
        CATEGORY_PRESETS.put("Alliums", "produceFloat");
        CATEGORY_PRESETS.put("Fresh Herbs", "herbSway");
        CATEGORY_PRESETS.put("Leafy greens", "leafFlutter");
        CATEGORY_PRESETS.put("Tomatoes", "produceFloat");
        CATEGORY_PRESETS.put("Peppers", "produceFloat");
        CATEGORY_PRESETS.put("Root vegetables", "produceFloat");
        CATEGORY_PRESETS.put("Potatoes & starchy", "produceFloat");
        CATEGORY_PRESETS.put("Vegetables", "produceFloat");
        CATEGORY_PRESETS.put("Beans & Legumes", "produceFloat");
        CATEGORY_PRESETS.put("Grains & Rice", "grainScatter");
        CATEGORY_PRESETS.put("Pasta & Noodles", "grainScatter");
        CATEGORY_PRESETS.put("Bread & Bakery", "breadRise");
        CATEGORY_PRESETS.put("Fruit", "fruitPop");
        CATEGORY_PRESETS.put("Tropical fruit", "fruitPop");
        CATEGORY_PRESETS.put("Berries", "fruitPop");
        CATEGORY_PRESETS.put("Citrus", "fruitPop");
        CATEGORY_PRESETS.put("Dairy", "dairyWobble");
        CATEGORY_PRESETS.put("Cheese", "cheeseStretch");
        CATEGORY_PRESETS.put("Eggs", "proteinBounce");
        CATEGORY_PRESETS.put("Poultry", "proteinBounce");
        CATEGORY_PRESETS.put("Red meat", "proteinBounce");
        CATEGORY_PRESETS.put("Fish & Seafood", "fishSwim");
        CATEGORY_PRESETS.put("Oils", "liquidPour");
        CATEGORY_PRESETS.put("Sauces & Condiments", "pourOrDrip");
        CATEGORY_PRESETS.put("Common Spices", "spiceSprinkle");
        CATEGORY_PRESETS.put("Nuts & Seeds", "grainScatter");
        CATEGORY_PRESETS.put("Sweeteners", "pourOrDrip");
        CATEGORY_PRESETS.put("Baking", "grainScatter");
        CATEGORY_PRESETS.put("Drinks", "liquidPour");
        CATEGORY_PRESETS.put("Soups & Stocks", "steam");
        CATEGORY_PRESETS.put("African pantry", "spiceSprinkle");
        CATEGORY_PRESETS.put("Asian pantry", "spiceSprinkle");
        CATEGORY_PRESETS.put("Latin pantry", "spiceSprinkle");
        CATEGORY_PRESETS.put("Other", "produceFloat");
    }

    static final String GENERIC_ID = "ingredient_generic";
    static final int KEPT_MANIFESTS = 5;
    static final int MAX_REUSABLE = 80;
    static final int IMPORT_CHUNK = 250;
    static final int MAX_IMPORT_PER_CALL = 1000;

    private final IngredientVisualRepository visuals;
    private final IngredientCatalogReleaseRepository releases;
    private final UnmatchedIngredientRepository unmatched;
    private final UserRepository users;
    private final IngredientRepository ingredients;
    private final PlatformTransactionManager txManager;
    private final ObjectMapper json;

    @Override
    @Transactional(readOnly = true)
    public IngredientLibraryResponse library(IngredientVisualFilter f, String sort, int page, int size) {
        int p = Math.max(0, page), s = Math.min(Math.max(1, size), 100);
        Page<IngredientVisual> rows = visuals.findAll(IngredientCatalogSpecs.visuals(f), PageRequest.of(p, s, sort(sort)));

        Map<String, Long> tabs = new LinkedHashMap<>();
        IngredientVisualFilter base = f.withoutStatus();
        tabs.put("ALL", visuals.count(IngredientCatalogSpecs.visuals(base)));
        for (IngredientVisualStatus st : IngredientVisualStatus.values()) {
            tabs.put(st.name(), visuals.count(IngredientCatalogSpecs.visuals(base.withStatus(st))));
        }
        return new IngredientLibraryResponse(rows.getContent().stream().map(IngredientCatalogServiceImpl::toResponse).toList(),
                rows.getTotalElements(), p, s, rows.getTotalPages(), tabs, kpis(), visuals.categories(),
                visuals.collections(), releaseState());
    }

    private static Sort sort(String sort) {
        if ("name".equalsIgnoreCase(sort)) return Sort.by("name").ascending();
        if ("category".equalsIgnoreCase(sort)) return Sort.by("category", "name").ascending();
        if ("status".equalsIgnoreCase(sort)) return Sort.by("status").ascending().and(Sort.by("name"));
        return Sort.by("updatedAt").descending().and(Sort.by("name"));
    }

    private IngredientLibraryResponse.Kpis kpis() {
        Map<IngredientVisualStatus, Long> by = new EnumMap<>(IngredientVisualStatus.class);
        for (IngredientVisualStatus st : IngredientVisualStatus.values()) {
            by.put(st, visuals.count((r, q, cb) -> cb.equal(r.get("status"), st)));
        }
        long total = by.values().stream().mapToLong(Long::longValue).sum();
        return new IngredientLibraryResponse.Kpis(total, by.get(IngredientVisualStatus.COMPLETE),
                by.get(IngredientVisualStatus.NEEDS_REVIEW), by.get(IngredientVisualStatus.MISSING_ASSET),
                by.get(IngredientVisualStatus.MISSING_ANIMATION), by.get(IngredientVisualStatus.DISABLED),
                unmatched.countByStatus(UnmatchedStatus.OPEN),
                unmatched.countByStatusAndFirstSeenAtAfter(UnmatchedStatus.OPEN, LocalDateTime.now().minusDays(7)),
                visuals.countAliases());
    }

    private IngredientLibraryResponse.Release releaseState() {
        Optional<IngredientCatalogRelease> last = releases.findTopByOrderByVersionDesc();
        if (last.isEmpty()) return new IngredientLibraryResponse.Release(0, null, null, 0, 1, visuals.count());
        IngredientCatalogRelease r = last.get();
        return new IngredientLibraryResponse.Release(r.getVersion(), r.getPublishedAt(), r.getPublishedBy(),
                r.getIngredientCount(), r.getVersion() + 1, visuals.countByUpdatedAtAfter(r.getPublishedAt()));
    }

    @Override
    @Transactional(readOnly = true)
    public IngredientVisualResponse get(UUID id) {
        return toResponse(find(id));
    }

    @Override
    @Transactional(readOnly = true)
    public IngredientCatalogMetaResponse meta() {
        TreeSet<String> categories = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        categories.addAll(CATEGORY_PRESETS.keySet());
        categories.addAll(visuals.categories());
        Map<String, IngredientCatalogMetaResponse.Reusable> families = new LinkedHashMap<>();
        for (IngredientVisual v : visuals.findPublishable()) {
            if (!v.isReviewed() || GENERIC_ID.equals(v.getCanonicalId())) continue;
            String family = family(v.getArchetype(), v.getCanonicalId());
            if (families.size() >= MAX_REUSABLE || families.containsKey(family)) continue;
            families.put(family, new IngredientCatalogMetaResponse.Reusable(v.getCanonicalId(), v.getName(), family,
                    v.getMainColor(), v.getSvg()));
        }
        return new IngredientCatalogMetaResponse(PRESETS, new ArrayList<>(categories), visuals.collections(),
                CATEGORY_PRESETS, new ArrayList<>(families.values()), SvgAssets.MAX_BYTES,
                visuals.count() + visuals.countAliases(),
                visuals.findByCanonicalId(GENERIC_ID).map(IngredientVisual::getSvg).orElse(null));
    }

    /** "pod.scallion" → "pod"; without an archetype the canonical id stands for its own family. */
    static String family(String archetype, String canonicalId) {
        if (archetype == null || archetype.isBlank()) return canonicalId;
        int dot = archetype.indexOf('.');
        return dot > 0 ? archetype.substring(0, dot) : archetype;
    }

    @Override
    @Transactional(readOnly = true)
    public IngredientNameCheckResponse checkName(String name, UUID excludeId) {
        String key = IngredientKeys.key(name);
        if (key.isEmpty()) return new IngredientNameCheckResponse(name, key, false, null);
        return visuals.findByAnyKey(List.of(key)).stream()
                .filter(v -> !v.getId().equals(excludeId))
                .findFirst()
                .map(v -> new IngredientNameCheckResponse(name, key, false,
                        new IngredientNameCheckResponse.Owner(v.getId(), v.getCanonicalId(), v.getName())))
                .orElseGet(() -> new IngredientNameCheckResponse(name, key, true, null));
    }

    @Override
    @Transactional
    public IngredientVisualResponse create(IngredientVisualCreateRequest r, String adminEmail) {
        String canonicalId = r.canonicalId().trim();
        if (!IngredientKeys.isCanonicalId(canonicalId)) throw new BadRequestException("The canonical id must be snake_case.");
        if (visuals.existsByCanonicalId(canonicalId)) throw new BadRequestException("The canonical id " + canonicalId + " is already used.");
        String name = r.name().trim();
        String nameKey = IngredientKeys.key(name);
        if (nameKey.isEmpty()) throw new BadRequestException("The name needs at least one letter or digit.");
        ensureFree(List.of(name, canonicalId), null, true);

        IngredientVisual v = IngredientVisual.builder()
                .canonicalId(canonicalId)
                .name(name)
                .nameKey(nameKey)
                .category(r.category().trim())
                .collection(blankToNull(r.collection()))
                .animation(preset(r.animation()))
                .archetype(blankToNull(r.archetype()))
                .delivery(r.delivery() == null ? IngredientDelivery.CDN : r.delivery())
                .mainColor(r.mainColor())
                .reviewed(false)
                .build();
        v.setAliases(aliases(r.aliases(), v, null));

        if (r.svg() != null && !r.svg().isBlank()) {
            setSvg(v, r.svg());
        } else if (r.looksLike() != null && !r.looksLike().isBlank()) {
            IngredientVisual src = visuals.findByCanonicalId(r.looksLike().trim())
                    .filter(x -> x.getSvg() != null)
                    .orElseThrow(() -> new BadRequestException("There is no art to reuse for " + r.looksLike() + "."));
            setSvg(v, SvgAssets.recolor(src.getSvg(), r.mainColor()));
            if (v.getArchetype() == null) v.setArchetype(family(src.getArchetype(), src.getCanonicalId()) + "." + canonicalId);
        }
        if (r.mainColor() != null) v.setMainColor(r.mainColor().toUpperCase(Locale.ROOT));
        v.touch(displayName(adminEmail));
        IngredientVisual saved = visuals.save(v);
        resolveQueue(saved, adminEmail);
        if (r.fromUnmatchedId() != null) {
            unmatched.findById(r.fromUnmatchedId()).ifPresent(u -> markResolved(u, saved.getId(), adminEmail));
        }
        return toResponse(saved);
    }

    @Override
    @Transactional
    public IngredientVisualResponse update(UUID id, IngredientVisualUpdateRequest r, String adminEmail) {
        IngredientVisual v = find(id);
        if (r.name() != null && !r.name().isBlank() && !r.name().trim().equals(v.getName())) {
            String name = r.name().trim();
            ensureFree(List.of(name), v.getId(), true);
            v.setName(name);
            v.setNameKey(IngredientKeys.key(name));
        }
        if (r.category() != null && !r.category().isBlank()) v.setCategory(r.category().trim());
        if (r.collection() != null) v.setCollection(blankToNull(r.collection()));
        if (r.animation() != null) v.setAnimation(preset(r.animation()));
        if (r.archetype() != null) v.setArchetype(blankToNull(r.archetype()));
        if (r.delivery() != null) v.setDelivery(r.delivery());
        if (r.mainColor() != null) v.setMainColor(r.mainColor().toUpperCase(Locale.ROOT));
        if (r.reviewed() != null) v.setReviewed(r.reviewed());
        if (r.enabled() != null) {
            if (!r.enabled() && GENERIC_ID.equals(v.getCanonicalId())) {
                throw new BadRequestException("The generic art is the fallback of every unknown ingredient; it cannot be disabled.");
            }
            v.setEnabled(r.enabled());
        }
        if (r.aliases() != null) {
            List<IngredientAlias> next = aliases(r.aliases(), v, v.getId());
            v.getAliases().clear();
            v.getAliases().addAll(next);
        }
        v.touch(displayName(adminEmail));
        IngredientVisual saved = visuals.save(v);
        resolveQueue(saved, adminEmail);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public IngredientVisualResponse replaceAsset(UUID id, String svg, String adminEmail) {
        IngredientVisual v = find(id);
        setSvg(v, svg);
        v.setReviewed(true);
        v.touch(displayName(adminEmail));
        return toResponse(visuals.save(v));
    }

    @Override
    @Transactional
    public IngredientVisualResponse addAlias(UUID id, String alias, String adminEmail) {
        IngredientVisual v = find(id);
        List<String> names = new ArrayList<>(v.getAliases().stream().map(IngredientAlias::getAlias).toList());
        names.add(alias);
        List<IngredientAlias> next = aliases(names, v, v.getId());
        v.getAliases().clear();
        v.getAliases().addAll(next);
        v.touch(displayName(adminEmail));
        IngredientVisual saved = visuals.save(v);
        resolveQueue(saved, adminEmail);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public IngredientReleaseResponse publish(String notes, String adminEmail) {
        List<IngredientVisual> ready = visuals.findPublishable();
        if (ready.isEmpty()) throw new BadRequestException("Nothing to publish: no enabled ingredient has art yet.");
        int version = releases.findTopByOrderByVersionDesc().map(IngredientCatalogRelease::getVersion).orElse(0) + 1;
        LocalDateTime now = LocalDateTime.now();
        IngredientCatalogRelease release = releases.save(IngredientCatalogRelease.builder()
                .version(version)
                .publishedAt(now)
                .publishedBy(displayName(adminEmail))
                .ingredientCount(ready.size())
                .notes(blankToNull(notes))
                .manifest(manifest(ready, version, now))
                .build());
        if (version > KEPT_MANIFESTS) releases.clearManifestsUpTo(version - KEPT_MANIFESTS);
        return toResponse(release);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IngredientReleaseResponse> releases() {
        return releases.findTop20ByOrderByVersionDesc().stream().map(IngredientCatalogServiceImpl::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public String draftManifest() {
        int next = releases.findTopByOrderByVersionDesc().map(IngredientCatalogRelease::getVersion).orElse(0) + 1;
        return manifest(visuals.findPublishable(), next, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> publishedManifest() {
        return releases.findTopByOrderByVersionDesc().map(IngredientCatalogRelease::getManifest);
    }

    /** JSON the app downloads: every enabled ingredient with art, its aliases and animation. */
    private String manifest(List<IngredientVisual> list, int version, LocalDateTime publishedAt) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", version);
        root.put("publishedAt", publishedAt == null ? null : publishedAt.toString());
        root.put("draft", publishedAt == null);
        root.put("fallback", GENERIC_ID);
        root.put("defaultAnimation", PRESETS.get(0));
        root.put("count", list.size());
        List<Map<String, Object>> items = new ArrayList<>();
        for (IngredientVisual v : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", v.getCanonicalId());
            m.put("name", v.getName());
            m.put("category", v.getCategory());
            m.put("collection", v.getCollection());
            m.put("aliases", v.getAliases().stream().map(IngredientAlias::getAlias).toList());
            m.put("keys", keysOf(v));
            m.put("animation", v.getAnimation() == null ? PRESETS.get(0) : v.getAnimation());
            m.put("delivery", v.getDelivery().name());
            m.put("hash", v.getSvgHash());
            m.put("svg", v.getSvg());
            items.add(m);
        }
        root.put("ingredients", items);
        try {
            return json.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not write the ingredient manifest", e);
        }
    }

    @Override
    public ImportProgressResponse importDatabaseIngredients(String adminEmail, int max) {
        int limit = Math.min(Math.max(1, max), MAX_IMPORT_PER_CALL);
        TransactionTemplate tx = new TransactionTemplate(txManager);
        Set<String> taken = new HashSet<>();
        tx.executeWithoutResult(st -> {
            visuals.nameKeys().forEach(r -> { taken.add((String) r[1]); taken.add((String) r[2]); });
            visuals.aliasKeys().forEach(r -> taken.add((String) r[1]));
            // disabled visuals still own their names
            visuals.findAll((r, q, cb) -> cb.isFalse(r.get("enabled"))).forEach(v -> taken.addAll(keysOf(v)));
        });
        String by = displayName(adminEmail);
        List<IngredientVisual> batch = new ArrayList<>();
        List<String> batchKeys = new ArrayList<>();
        int created = 0, skipped = 0, remaining = 0;
        List<Object[]> rows = tx.execute(st -> ingredients.namesByUsage());
        for (Object[] row : rows == null ? List.<Object[]>of() : rows) {
            String raw = row[0] == null ? "" : row[0].toString().trim().replaceAll("\\s+", " ");
            String key = IngredientKeys.key(raw);
            String canonicalId = canonicalFrom(key);
            if (raw.length() > IngredientKeys.MAX_LENGTH || canonicalId == null || taken.contains(key) || taken.contains(canonicalId)) {
                skipped++;
                continue;
            }
            taken.add(key);
            taken.add(canonicalId);
            if (created + batch.size() >= limit) {
                remaining++; // left for the next call
                continue;
            }
            String category = IngredientCategories.guess(key);
            IngredientVisual v = IngredientVisual.builder()
                    .canonicalId(canonicalId)
                    .name(Character.toUpperCase(raw.charAt(0)) + raw.substring(1))
                    .nameKey(key)
                    .category(category)
                    .animation(CATEGORY_PRESETS.getOrDefault(category, PRESETS.get(0)))
                    .delivery(IngredientDelivery.CDN)
                    .reviewed(false)
                    .build();
            v.touch(by);
            batch.add(v);
            batchKeys.add(key);
            if (batch.size() >= IMPORT_CHUNK) {
                saveImported(tx, batch, batchKeys, adminEmail);
                created += batch.size();
                batch.clear();
                batchKeys.clear();
            }
        }
        if (!batch.isEmpty()) {
            saveImported(tx, batch, batchKeys, adminEmail);
            created += batch.size();
        }
        return new ImportProgressResponse(created, skipped, remaining);
    }

    /** One transaction per chunk: a large import never holds one huge transaction. */
    private void saveImported(TransactionTemplate tx, List<IngredientVisual> batch, List<String> keys, String adminEmail) {
        List<IngredientVisual> copy = new ArrayList<>(batch);
        List<String> keyCopy = new ArrayList<>(keys);
        tx.executeWithoutResult(st -> {
            Map<String, UUID> ids = new HashMap<>();
            visuals.saveAll(copy).forEach(v -> ids.put(v.getNameKey(), v.getId()));
            unmatched.findByNameKeyInAndStatus(keyCopy, UnmatchedStatus.OPEN)
                    .forEach(u -> markResolved(u, ids.get(u.getNameKey()), adminEmail));
        });
    }

    /** The key itself when it is a valid permanent id; quantities ("2_tomato") and very long names are left out. */
    static String canonicalFrom(String key) {
        return IngredientKeys.isCanonicalId(key) ? key : null;
    }

    @Override
    @Transactional
    public CatalogSeedResponse installStarterPack(String adminEmail) {
        List<StarterItem> pack;
        try (InputStream in = new ClassPathResource("ingredient_starter_pack.json").getInputStream()) {
            pack = json.readValue(in, new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new IllegalStateException("The ingredient starter pack is missing", e);
        }
        int created = 0, skipped = 0;
        for (StarterItem s : pack) {
            List<String> keys = new ArrayList<>();
            keys.add(IngredientKeys.key(s.name()));
            keys.add(s.canonicalId());
            s.aliases().forEach(a -> keys.add(IngredientKeys.key(a)));
            if (visuals.existsByCanonicalId(s.canonicalId()) || !visuals.findByAnyKey(keys).isEmpty()) {
                skipped++;
                continue;
            }
            IngredientVisual v = IngredientVisual.builder()
                    .canonicalId(s.canonicalId())
                    .name(s.name())
                    .nameKey(IngredientKeys.key(s.name()))
                    .category(s.category())
                    .collection(s.collection())
                    .animation(s.animation())
                    .archetype(s.archetype())
                    .delivery(IngredientDelivery.valueOf(s.delivery()))
                    .reviewed(true)
                    .build();
            v.setAliases(aliases(s.aliases(), v, null));
            setSvg(v, s.svg());
            v.touch("Starter pack");
            resolveQueue(visuals.save(v), adminEmail);
            created++;
        }
        return new CatalogSeedResponse(created, skipped);
    }

    record StarterItem(String canonicalId, String name, String category, String collection, List<String> aliases,
                       String animation, String archetype, String delivery, String svg) {
    }

    // ---------------------------------------------------------------- helpers

    private IngredientVisual find(UUID id) {
        return visuals.findById(id).orElseThrow(() -> new ResourceNotFoundException("Ingredient not found"));
    }

    /** Throws when one of these names already resolves to another visual. */
    private void ensureFree(List<String> names, UUID self, boolean asName) {
        Map<String, String> byKey = new LinkedHashMap<>();
        names.forEach(n -> byKey.putIfAbsent(IngredientKeys.key(n), n));
        byKey.remove("");
        if (byKey.isEmpty()) return;
        for (IngredientVisual other : visuals.findByAnyKey(byKey.keySet())) {
            if (other.getId().equals(self)) continue;
            Set<String> taken = new HashSet<>(keysOf(other));
            String clash = byKey.entrySet().stream().filter(e -> taken.contains(e.getKey())).map(Map.Entry::getValue)
                    .findFirst().orElse(byKey.values().iterator().next());
            throw new BadRequestException(asName
                    ? "“" + clash + "” already resolves to " + other.getName() + ". Synonyms are aliases, not new ingredients."
                    : "“" + clash + "” already belongs to " + other.getName() + ". An alias can only point to one ingredient.");
        }
    }

    /** Clean alias list: trimmed, one per key, not repeating the name or id, not owned by another visual. */
    private List<IngredientAlias> aliases(List<String> raw, IngredientVisual v, UUID self) {
        if (raw == null) return new ArrayList<>();
        Set<String> own = new HashSet<>(List.of(v.getNameKey(), v.getCanonicalId()));
        Map<String, String> byKey = new LinkedHashMap<>();
        for (String a : raw) {
            if (a == null || a.isBlank()) continue;
            String alias = a.trim();
            if (alias.length() > 120) throw new BadRequestException("Aliases are at most 120 characters.");
            String key = IngredientKeys.key(alias);
            if (key.isEmpty() || own.contains(key)) continue;
            byKey.putIfAbsent(key, alias);
        }
        if (byKey.size() > 30) throw new BadRequestException("An ingredient has at most 30 aliases.");
        ensureFree(new ArrayList<>(byKey.values()), self, false);
        return byKey.entrySet().stream().map(e -> new IngredientAlias(e.getValue(), e.getKey()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static void setSvg(IngredientVisual v, String raw) {
        String svg = SvgAssets.clean(raw);
        List<String> problems = SvgAssets.problems(svg);
        if (!problems.isEmpty()) throw new BadRequestException(String.join(" ", problems));
        v.setSvg(svg);
        v.setSvgBytes(SvgAssets.bytes(svg));
        v.setSvgHash(SvgAssets.hash(svg));
        if (v.getMainColor() == null) SvgAssets.mainColor(svg).ifPresent(v::setMainColor);
    }

    private static String preset(String animation) {
        if (animation == null || animation.isBlank()) return null;
        String a = animation.trim();
        if (!PRESETS.contains(a)) throw new BadRequestException("Unknown animation preset: " + a);
        return a;
    }

    static List<String> keysOf(IngredientVisual v) {
        List<String> keys = new ArrayList<>();
        keys.add(v.getNameKey());
        if (!v.getCanonicalId().equals(v.getNameKey())) keys.add(v.getCanonicalId());
        v.getAliases().forEach(a -> keys.add(a.getAliasKey()));
        return keys;
    }

    /** Open Not-in-catalog names this visual now resolves are closed with it. */
    private void resolveQueue(IngredientVisual v, String adminEmail) {
        if (!v.isEnabled()) return;
        unmatched.findByNameKeyInAndStatus(keysOf(v), UnmatchedStatus.OPEN).forEach(u -> markResolved(u, v.getId(), adminEmail));
    }

    private void markResolved(UnmatchedIngredient u, UUID visualId, String adminEmail) {
        u.setStatus(UnmatchedStatus.RESOLVED);
        u.setResolvedVisualId(visualId);
        u.setResolvedAt(LocalDateTime.now());
        u.setResolvedBy(adminEmail);
        unmatched.save(u);
    }

    /** "Cheikh Gueye" → "Cheikh G."; the e-mail's local part when the account has no name. */
    private String displayName(String email) {
        if (email == null) return null;
        return users.findByEmail(email).map(u -> {
            String first = u.getFirstname() == null ? "" : u.getFirstname().trim();
            String last = u.getLastname() == null ? "" : u.getLastname().trim();
            if (first.isEmpty()) return email.split("@")[0];
            return last.isEmpty() ? first : first + " " + last.charAt(0) + ".";
        }).orElse(email.split("@")[0]);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static IngredientVisualResponse toResponse(IngredientVisual v) {
        return new IngredientVisualResponse(v.getId(), v.getCanonicalId(), v.getName(), v.getCategory(), v.getCollection(),
                v.getAliases().stream().map(IngredientAlias::getAlias).toList(), v.getAnimation(), v.getSvg(),
                v.getSvg() == null ? null : v.getCanonicalId() + ".svg", v.getSvgBytes(),
                v.getSvgBytes() == null ? null : SvgAssets.kb(v.getSvgBytes()), v.getSvgHash(), v.getArchetype(),
                v.getDelivery(), v.getMainColor(), v.isEnabled(), v.isReviewed(), v.getStatus(), v.getUpdatedAt(),
                v.getUpdatedBy());
    }

    static IngredientReleaseResponse toResponse(IngredientCatalogRelease r) {
        return new IngredientReleaseResponse(r.getId(), r.getVersion(), r.getPublishedAt(), r.getPublishedBy(),
                r.getIngredientCount(), r.getNotes(), r.getManifest() != null);
    }
}
