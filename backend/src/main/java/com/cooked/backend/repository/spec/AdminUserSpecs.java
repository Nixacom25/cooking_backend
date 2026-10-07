package com.cooked.backend.repository.spec;

import com.cooked.backend.dto.request.AdminUserFilter;
import com.cooked.backend.entity.Ambassador;
import com.cooked.backend.entity.DeviceSession;
import com.cooked.backend.entity.Role;
import com.cooked.backend.entity.Status;
import com.cooked.backend.entity.SubscriptionStatus;
import com.cooked.backend.entity.User;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds the admin Users query from {@link AdminUserFilter}; unknown enum values match nothing. */
public final class AdminUserSpecs {

    /** Device name recorded for app logins without an OS header (older builds), lower-cased. */
    public static final String MOBILE_APP = "cooked mobile app";

    private AdminUserSpecs() {
    }

    public static Specification<User> of(AdminUserFilter f, LocalDateTime now) {
        return (root, query, cb) -> {
            List<Predicate> and = new ArrayList<>();

            if (Boolean.TRUE.equals(f.partner())) {
                Subquery<String> ambassadorEmails = query.subquery(String.class);
                var a = ambassadorEmails.from(Ambassador.class);
                ambassadorEmails.select(cb.lower(a.get("email"))).where(cb.isNotNull(a.get("email")));
                and.add(cb.or(cb.equal(root.get("role"), Role.CREATOR), cb.lower(root.get("email")).in(ambassadorEmails)));
            } else {
                and.add(cb.equal(root.get("role"), Role.CLIENT));
            }

            if (notBlank(f.q())) {
                String like = "%" + f.q().trim().toLowerCase(Locale.ROOT) + "%";
                and.add(cb.or(
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(cb.concat(cb.concat(cb.coalesce(root.get("firstname"), ""), " "), cb.coalesce(root.get("lastname"), ""))), like),
                        cb.like(cb.lower(cb.coalesce(root.get("revenueCatCustomerId"), "")), like),
                        cb.like(cb.lower(root.get("id").as(String.class)), like)));
            }

            if (notBlank(f.platform())) {
                String platform = f.platform().trim().toUpperCase(Locale.ROOT);
                Subquery<Long> sessions = query.subquery(Long.class);
                var s = sessions.from(DeviceSession.class);
                var name = cb.lower(cb.coalesce(s.get("deviceName"), ""));
                Predicate onPlatform = switch (platform) {
                    case "IOS" -> cb.like(name, "ios%");
                    case "ANDROID" -> cb.like(name, "android%");
                    // Mobile app builds that don't report their OS yet.
                    case "APP" -> cb.equal(name, MOBILE_APP);
                    default -> cb.and(cb.notLike(name, "ios%"), cb.notLike(name, "android%"), cb.notEqual(name, MOBILE_APP));
                };
                sessions.select(cb.literal(1L)).where(cb.equal(s.get("user"), root), onPlatform);
                and.add(cb.exists(sessions));
            }

            if (notBlank(f.subscription())) {
                SubscriptionStatus st = enumOrNull(SubscriptionStatus.class, f.subscription());
                and.add(st == null ? cb.disjunction() : cb.equal(root.get("subscriptionStatus"), st));
            }
            if (Boolean.TRUE.equals(f.trial())) {
                and.add(cb.equal(root.get("subscriptionStatus"), SubscriptionStatus.TRIAL));
            }
            if (notBlank(f.status())) {
                Status st = enumOrNull(Status.class, f.status());
                and.add(st == null ? cb.disjunction() : cb.equal(root.get("status"), st));
            }
            if (f.signupDays() != null && f.signupDays() > 0) {
                and.add(cb.greaterThanOrEqualTo(root.get("createdAt"), now.minusDays(Math.min(f.signupDays(), 3650))));
            }
            if (notBlank(f.source())) {
                and.add(cb.equal(cb.lower(cb.trim(root.get("discoverySource"))), f.source().trim().toLowerCase(Locale.ROOT)));
            }
            return cb.and(and.toArray(Predicate[]::new));
        };
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static <E extends Enum<E>> E enumOrNull(Class<E> type, String name) {
        try {
            return Enum.valueOf(type, name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
