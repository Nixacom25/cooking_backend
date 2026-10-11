package com.cooked.backend.security;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Which area of the backoffice a request belongs to, for custom roles.
 * GET/HEAD need View (or Edit); other methods need Edit. Paths every staff
 * member needs (own profile, 2FA, alerts, saved views…) are COMMON.
 * Anything not listed is denied to custom-role members (deny by default):
 * a new admin endpoint must be added here to be reachable by them.
 */
public final class RolePolicy {

    public enum Level { NONE, VIEW, EDIT }

    public static final List<String> AREAS = List.of("users", "recipes", "revenue", "costs", "growth", "support", "analytics", "settings");
    public static final String COMMON = "common";

    private RolePolicy() {}

    private static final Pattern USER_ID_ACTION = Pattern.compile("^/user/[0-9a-fA-F-]{36}/(status|subscription)$");
    private static final Pattern USER_ID_ROLE = Pattern.compile("^/user/[0-9a-fA-F-]{36}/role$");

    /** Longest prefix wins (sorted by length at class load). */
    private static final List<Map.Entry<String, String>> PREFIXES = new ArrayList<>();
    static {
        Map<String, String> m = new LinkedHashMap<>();
        // Common to every staff member
        for (String p : List.of("/user/me", "/user/last-active", "/user/fcm-token", "/user/profile-photo", "/user/notification-preferences",
                "/sessions", "/api/admin/2fa", "/api/admin/views", "/api/admin/alerts", "/api/notifications", "/api/admin/roles/me", "/auth")) {
            m.put(p, COMMON);
        }
        for (String p : List.of("/api/admin/users", "/user/client")) m.put(p, "users");
        for (String p : List.of("/api/admin/recipes", "/api/admin/categories", "/api/admin/ingredient-catalog", "/api/recipe-data",
                "/api/ingredients", "/admin/seed", "/user/editors", "/api/admin/insights/recipes")) m.put(p, "recipes");
        for (String p : List.of("/api/admin/revenue", "/api/analytics/subscriptions", "/api/analytics/transactions",
                "/api/admin/insights/billing", "/api/admin/insights/paying-by-feature")) m.put(p, "revenue");
        m.put("/api/admin/costs", "costs");
        for (String p : List.of("/api/admin/email-campaigns", "/notification-campaigns", "/api/admin/ambassadors", "/api/admin/creator-applications",
                "/api/admin/creators", "/api/admin/articles", "/api/admin/seo", "/api/admin/research-sources", "/user/creators",
                "/api/admin/insights/articles", "/api/admin/insights/creators", "/api/admin/email", "/api/admin/automations")) m.put(p, "growth");
        for (String p : List.of("/api/admin/support", "/support/tickets", "/errors", "/api/admin/incidents")) m.put(p, "support");
        for (String p : List.of("/api/admin/analytics", "/api/admin/trends", "/api/admin/dashboard", "/api/kpi", "/api/admin/insights/sources",
                "/api/analytics/firebase", "/api/admin/ask")) m.put(p, "analytics");
        for (String p : List.of("/api/admin/settings", "/api/admin/integrations", "/api/admin/team", "/api/admin/roles", "/api/admin/exports",
                "/activities", "/user/admin", "/api/admin/system")) m.put(p, "settings");
        m.forEach((k, v) -> PREFIXES.add(Map.entry(k, v)));
        PREFIXES.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
    }

    /** True for paths custom roles are checked on (staff-only areas of the API). */
    public static boolean isStaffPath(String path) {
        return path.startsWith("/api/admin") || path.startsWith("/user") || path.startsWith("/activities") || path.startsWith("/support/tickets")
                || path.startsWith("/errors") || path.startsWith("/notification-campaigns") || path.startsWith("/api/kpi")
                || path.startsWith("/api/recipe-data") || path.startsWith("/api/ingredients") || path.startsWith("/admin/seed")
                || path.startsWith("/api/analytics") || path.startsWith("/api/notifications");
    }

    /** The area of a path, COMMON, or null when unknown (denied). */
    public static String areaOf(String path) {
        if (USER_ID_ACTION.matcher(path).matches()) return "users";
        if (USER_ID_ROLE.matcher(path).matches()) return "settings";
        for (Map.Entry<String, String> e : PREFIXES) {
            String p = e.getKey();
            if (path.equals(p) || path.startsWith(p + "/") || path.startsWith(p + "?")) return e.getValue();
        }
        return null;
    }

    /** Ask Cooked is read-only even though it is a POST. */
    static boolean readOnly(String method, String path) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)
                || path.equals("/api/admin/ask") || path.startsWith("/api/admin/ask/");
    }

    /** Whether a member of a role with these permissions may make this request. */
    public static boolean allowed(Map<String, Level> permissions, String method, String path) {
        if (!isStaffPath(path)) return true; // the app's own endpoints are not part of the backoffice
        String area = areaOf(path);
        if (COMMON.equals(area)) return true;
        if (area == null) return false;
        Level level = permissions.getOrDefault(area, Level.NONE);
        return readOnly(method, path) ? level != Level.NONE : level == Level.EDIT;
    }

    /** "users=VIEW,recipes=EDIT" ↔ map; unknown areas and values are ignored. */
    public static Map<String, Level> parse(String s) {
        Map<String, Level> out = new LinkedHashMap<>();
        for (String a : AREAS) out.put(a, Level.NONE);
        if (s == null) return out;
        for (String part : s.split(",")) {
            String[] kv = part.split("=");
            if (kv.length != 2 || !AREAS.contains(kv[0].trim())) continue;
            try {
                out.put(kv[0].trim(), Level.valueOf(kv[1].trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return out;
    }

    public static String format(Map<String, Level> perms) {
        StringJoiner j = new StringJoiner(",");
        for (String a : AREAS) j.add(a + "=" + perms.getOrDefault(a, Level.NONE).name());
        return j.toString();
    }
}
