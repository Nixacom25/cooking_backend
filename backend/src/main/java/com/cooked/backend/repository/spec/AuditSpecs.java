package com.cooked.backend.repository.spec;

import com.cooked.backend.dto.request.AdminAuditFilter;
import com.cooked.backend.entity.ActivityLog;
import com.cooked.backend.entity.Role;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Editor activity (audit log) query from {@link AdminAuditFilter}, newest first. */
public final class AuditSpecs {

    private AuditSpecs() {
    }

    public static Specification<ActivityLog> of(Role role, AdminAuditFilter f, LocalDateTime now) {
        return (root, query, cb) -> {
            var user = root.join("user");
            List<Predicate> and = new ArrayList<>();
            and.add(cb.equal(user.get("role"), role));
            if (notBlank(f.person())) and.add(cb.equal(cb.lower(user.get("email")), f.person().trim().toLowerCase(Locale.ROOT)));
            if (notBlank(f.area())) and.add(cb.equal(cb.upper(root.get("entityType")), f.area().trim().toUpperCase(Locale.ROOT)));
            if (notBlank(f.action())) and.add(cb.equal(root.get("title"), f.action()));
            if (f.days() != null && f.days() > 0) and.add(cb.greaterThanOrEqualTo(root.get("createdAt"), now.minusDays(Math.min(f.days(), 3650))));
            if (notBlank(f.q())) {
                String like = "%" + f.q().trim().toLowerCase(Locale.ROOT) + "%";
                and.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("title"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("message"), "")), like),
                        cb.like(cb.lower(user.get("email")), like),
                        cb.like(cb.lower(cb.concat(cb.concat(cb.coalesce(user.get("firstname"), ""), " "), cb.coalesce(user.get("lastname"), ""))), like)));
            }
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                query.orderBy(cb.desc(root.get("createdAt")));
            }
            return cb.and(and.toArray(Predicate[]::new));
        };
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
