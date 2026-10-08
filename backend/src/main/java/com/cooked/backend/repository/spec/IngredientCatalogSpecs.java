package com.cooked.backend.repository.spec;

import com.cooked.backend.dto.request.IngredientVisualFilter;
import com.cooked.backend.dto.request.UnmatchedIngredientFilter;
import com.cooked.backend.entity.IngredientVisual;
import com.cooked.backend.entity.UnmatchedIngredient;
import com.cooked.backend.util.IngredientKeys;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Ingredient Library and Not-in-catalog queries. */
public final class IngredientCatalogSpecs {

    private IngredientCatalogSpecs() {
    }

    public static Specification<IngredientVisual> visuals(IngredientVisualFilter f) {
        return (root, query, cb) -> {
            List<Predicate> and = new ArrayList<>();
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().trim().toLowerCase(Locale.ROOT) + "%";
                String keyLike = "%" + IngredientKeys.key(f.q()) + "%";
                var sub = query.subquery(Integer.class);
                var v = sub.from(IngredientVisual.class);
                var a = v.join("aliases");
                sub.select(cb.literal(1)).where(cb.equal(v.get("id"), root.get("id")), cb.like(a.get("aliasKey"), keyLike));
                and.add(cb.or(cb.like(cb.lower(root.get("name")), like), cb.like(root.get("nameKey"), keyLike),
                        cb.like(root.get("canonicalId"), keyLike), cb.exists(sub)));
            }
            if (f.status() != null) and.add(cb.equal(root.get("status"), f.status()));
            if (f.category() != null && !f.category().isBlank()) and.add(cb.equal(root.get("category"), f.category().trim()));
            if (f.collection() != null && !f.collection().isBlank()) and.add(cb.equal(root.get("collection"), f.collection().trim()));
            if (f.animation() != null && !f.animation().isBlank()) {
                and.add("NONE".equalsIgnoreCase(f.animation()) ? cb.isNull(root.get("animation")) : cb.equal(root.get("animation"), f.animation().trim()));
            }
            if (f.delivery() != null) and.add(cb.equal(root.get("delivery"), f.delivery()));
            return cb.and(and.toArray(Predicate[]::new));
        };
    }

    public static Specification<UnmatchedIngredient> unmatched(UnmatchedIngredientFilter f) {
        return (root, query, cb) -> {
            List<Predicate> and = new ArrayList<>();
            if (f.q() != null && !f.q().isBlank()) {
                and.add(cb.or(cb.like(cb.lower(root.get("sampleName")), "%" + f.q().trim().toLowerCase(Locale.ROOT) + "%"),
                        cb.like(root.get("nameKey"), "%" + IngredientKeys.key(f.q()) + "%")));
            }
            if (f.status() != null) and.add(cb.equal(root.get("status"), f.status()));
            if (f.source() != null) {
                String col = switch (f.source()) {
                    case SCAN -> "scanCount";
                    case IMPORT -> "importCount";
                    case GROCERY -> "groceryCount";
                };
                and.add(cb.greaterThan(root.get(col), 0L));
            }
            if (f.days() != null && f.days() > 0) {
                and.add(cb.greaterThanOrEqualTo(root.get("lastSeenAt"), LocalDateTime.now().minusDays(f.days())));
            }
            return cb.and(and.toArray(Predicate[]::new));
        };
    }
}
