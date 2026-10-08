package com.cooked.backend.service.impl;

import java.util.Locale;
import java.util.regex.Pattern;

/** Validation of what the mobile app reports about itself (headers): never trusted as-is. */
final class ClientContext {

    private static final Pattern VERSION = Pattern.compile("[0-9]{1,4}(\\.[0-9]{1,4}){0,3}(\\+[0-9]{1,6})?");
    private static final Pattern COUNTRY = Pattern.compile("[A-Za-z]{2}");

    private ClientContext() {
    }

    /** "1.0.5+107" style versions only, else null. */
    static String version(String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        return VERSION.matcher(v).matches() ? v : null;
    }

    /** Two-letter ISO country (upper case), else null. */
    static String country(String raw) {
        if (raw == null) return null;
        String c = raw.trim();
        return COUNTRY.matcher(c).matches() ? c.toUpperCase(Locale.ROOT) : null;
    }
}
