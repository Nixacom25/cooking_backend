package com.cooked.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RolePolicyTest {

    private static final Map<String, RolePolicy.Level> SUPPORT_LEAD = RolePolicy.parse("support=EDIT,recipes=EDIT,revenue=VIEW,users=VIEW");

    @Test
    void viewReadsEditChangesNoneHides() {
        assertTrue(RolePolicy.allowed(SUPPORT_LEAD, "GET", "/api/admin/revenue/payments"));
        assertFalse(RolePolicy.allowed(SUPPORT_LEAD, "PATCH", "/api/admin/revenue/payments/1/status"));
        assertTrue(RolePolicy.allowed(SUPPORT_LEAD, "POST", "/api/admin/support/tickets/1/reply"));
        assertTrue(RolePolicy.allowed(SUPPORT_LEAD, "GET", "/api/admin/users"));
        assertFalse(RolePolicy.allowed(SUPPORT_LEAD, "PUT", "/user/123e4567-e89b-12d3-a456-426614174000/status"));
        assertFalse(RolePolicy.allowed(SUPPORT_LEAD, "GET", "/api/admin/costs/overview"));
        assertFalse(RolePolicy.allowed(SUPPORT_LEAD, "GET", "/api/admin/settings"));
        assertFalse(RolePolicy.allowed(SUPPORT_LEAD, "POST", "/api/admin/roles"));
        assertFalse(RolePolicy.allowed(SUPPORT_LEAD, "PUT", "/user/123e4567-e89b-12d3-a456-426614174000/role"));
    }

    @Test
    void commonAndAppPathsStayOpenUnknownStaffPathsAreDenied() {
        Map<String, RolePolicy.Level> none = RolePolicy.parse(null);
        assertTrue(RolePolicy.allowed(none, "PUT", "/user/me"));
        assertTrue(RolePolicy.allowed(none, "POST", "/api/admin/2fa/verify"));
        assertTrue(RolePolicy.allowed(none, "GET", "/api/admin/roles/me"));
        assertTrue(RolePolicy.allowed(none, "GET", "/recipes/popular"));
        assertFalse(RolePolicy.allowed(none, "GET", "/api/admin/something-new"));
        assertFalse(RolePolicy.allowed(none, "GET", "/api/admin/dashboard"));
        // Ask Cooked is read-only: View is enough.
        assertTrue(RolePolicy.allowed(RolePolicy.parse("analytics=VIEW"), "POST", "/api/admin/ask"));
    }

    @Test
    void permissionsRoundTrip() {
        assertEquals("users=VIEW,recipes=EDIT,revenue=NONE,costs=NONE,growth=NONE,support=NONE,analytics=NONE,settings=NONE",
                RolePolicy.format(RolePolicy.parse("recipes=EDIT,users=VIEW,bogus=EDIT,costs=ALL")));
    }

    /** Every staff endpoint must belong to an area, or role members could never reach it. */
    @Test
    void everyStaffEndpointHasAnArea() throws Exception {
        List<String> missing = new ArrayList<>();
        int checked = 0;
        for (Class<?> c : controllers()) {
            RequestMapping base = AnnotatedElementUtils.findMergedAnnotation(c, RequestMapping.class);
            String[] bases = base == null || base.path().length == 0 ? new String[]{""} : base.path();
            for (Method m : c.getDeclaredMethods()) {
                RequestMapping rm = AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class);
                if (rm == null) continue;
                String[] paths = rm.path().length == 0 ? new String[]{""} : rm.path();
                for (String b : bases) for (String p : paths) {
                    String full = (b + p).replaceAll("\\{[^}]+}", "123e4567-e89b-12d3-a456-426614174000");
                    if (!RolePolicy.isStaffPath(full)) continue;
                    if (!isStaffOnly(c, m)) continue;
                    checked++;
                    if (RolePolicy.areaOf(full) == null) missing.add(c.getSimpleName() + " " + (b + p));
                }
            }
        }
        assertTrue(checked > 150, "only " + checked + " staff endpoints found: the scan is broken");
        assertTrue(missing.isEmpty(), "Add these to RolePolicy: " + missing);
    }

    private static boolean isStaffOnly(Class<?> c, Method m) {
        var pm = AnnotatedElementUtils.findMergedAnnotation(m, org.springframework.security.access.prepost.PreAuthorize.class);
        var pc = AnnotatedElementUtils.findMergedAnnotation(c, org.springframework.security.access.prepost.PreAuthorize.class);
        String v = pm != null ? pm.value() : pc != null ? pc.value() : "";
        return v.contains("ADMIN") && !v.contains("CLIENT") && !v.contains("isAuthenticated") && !v.contains("permitAll");
    }

    private static List<Class<?>> controllers() throws Exception {
        // The main classes folder (the test classes have a controller package too).
        URL root = com.cooked.backend.controller.AdminRevenueController.class.getProtectionDomain().getCodeSource().getLocation();
        List<Class<?>> out = new ArrayList<>();
        for (File f : new File(new File(root.toURI()), "com/cooked/backend/controller").listFiles()) {
            String n = f.getName();
            if (!n.endsWith(".class") || n.contains("$")) continue;
            out.add(Class.forName("com.cooked.backend.controller." + n.replace(".class", "")));
        }
        return out;
    }
}
