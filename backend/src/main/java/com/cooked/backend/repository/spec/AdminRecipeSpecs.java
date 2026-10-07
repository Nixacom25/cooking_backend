package com.cooked.backend.repository.spec;

import com.cooked.backend.dto.request.AdminRecipeFilter;
import com.cooked.backend.entity.Recipe;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Admin Recipes query from {@link AdminRecipeFilter}, most recently changed first. */
public final class AdminRecipeSpecs {

    private AdminRecipeSpecs() {
    }

    public static Specification<Recipe> of(AdminRecipeFilter f) {
        return (root, query, cb) -> {
            List<Predicate> and = new ArrayList<>();
            if (f.origin() != null) and.add(cb.equal(root.get("origin"), f.origin()));
            if (f.name() != null && !f.name().isBlank()) {
                and.add(cb.like(cb.lower(root.get("name")), "%" + f.name().trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (f.cuisineId() != null) and.add(cb.equal(root.get("cuisine").get("id"), f.cuisineId()));
            if (f.visibility() != null && !f.visibility().isBlank()) {
                switch (f.visibility().trim().toUpperCase(Locale.ROOT)) {
                    case "PUBLIC" -> and.add(cb.and(cb.isTrue(root.get("isPublic")), cb.isFalse(root.get("isDeleted"))));
                    case "PRIVATE" -> and.add(cb.and(cb.isFalse(root.get("isPublic")), cb.isFalse(root.get("isDeleted"))));
                    case "DELETED" -> and.add(cb.isTrue(root.get("isDeleted")));
                    default -> and.add(cb.disjunction());
                }
            }
            if (f.hasImage() != null) {
                Predicate with = cb.and(cb.isNotNull(root.get("image")), cb.notEqual(cb.trim(root.get("image")), ""));
                and.add(f.hasImage() ? with : cb.not(with));
            }
            // Not on the count query (its result type is Long).
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                query.orderBy(cb.desc(cb.coalesce(root.get("updatedAt"), root.get("createdAt"))));
            }
            return cb.and(and.toArray(Predicate[]::new));
        };
    }
}
