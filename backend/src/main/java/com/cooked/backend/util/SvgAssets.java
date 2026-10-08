package com.cooked.backend.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rules for ingredient art: 64×64 SVG, at most 12 KB, no scripts, raster images, filters,
 * event handlers or external references. Also hashing and recolouring of a reused archetype.
 */
public final class SvgAssets {

    public static final int MAX_BYTES = 12 * 1024;

    private static final Pattern FORBIDDEN = Pattern.compile(
            "<\\s*(script|image|img|filter|fe[A-Za-z]+|foreignObject|iframe|use\\s[^>]*href\\s*=\\s*[\"']https?:)|\\son[a-z]+\\s*=|javascript:|(xlink:)?href\\s*=\\s*[\"']\\s*(https?:|data:)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern VIEWBOX = Pattern.compile("viewBox\\s*=\\s*[\"']\\s*0[ ,]+0[ ,]+64[ ,]+64\\s*[\"']");
    private static final Pattern HEX = Pattern.compile("#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})\\b");

    private SvgAssets() {
    }

    /** Problems that block saving the SVG (empty = valid). */
    public static List<String> problems(String svg) {
        List<String> out = new ArrayList<>();
        if (svg == null || svg.isBlank()) {
            out.add("The file is empty.");
            return out;
        }
        String s = clean(svg);
        if (!s.startsWith("<svg") || !s.endsWith("</svg>")) out.add("This is not an SVG file.");
        int bytes = s.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_BYTES) out.add("The SVG is " + kb(bytes) + "; the limit is 12 KB.");
        if (!VIEWBOX.matcher(s).find()) out.add("The SVG must use viewBox=\"0 0 64 64\".");
        if (FORBIDDEN.matcher(s).find()) out.add("Scripts, images, filters, event handlers and external links are not allowed.");
        return out;
    }

    /** Drops the XML declaration, comments and surrounding whitespace. */
    public static String clean(String svg) {
        return svg.replaceAll("(?s)<\\?xml.*?\\?>", "").replaceAll("(?s)<!--.*?-->", "").replaceAll("(?s)<!DOCTYPE.*?>", "").trim();
    }

    public static int bytes(String svg) {
        return svg.getBytes(StandardCharsets.UTF_8).length;
    }

    public static String hash(String svg) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(svg.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String kb(int bytes) {
        return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
    }

    /** The most used colour of the art, ignoring near-black outlines and near-white highlights. */
    public static Optional<String> mainColor(String svg) {
        Map<String, Integer> counts = new HashMap<>();
        Matcher m = HEX.matcher(svg);
        while (m.find()) {
            String c = expand(m.group(1));
            int lum = luminance(c);
            if (lum < 60 || lum > 235) continue;
            counts.merge(c, 1, Integer::sum);
        }
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(e -> "#" + e.getKey());
    }

    /** Replaces the art's main colour by {@code color} (#RRGGBB); returns the SVG unchanged when there is none. */
    public static String recolor(String svg, String color) {
        Optional<String> main = mainColor(svg);
        if (main.isEmpty() || color == null || !color.matches("#[0-9a-fA-F]{6}")) return svg;
        String target = main.get().substring(1);
        Matcher m = HEX.matcher(svg);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String c = expand(m.group(1));
            m.appendReplacement(sb, c.equals(target) ? color.toUpperCase() : m.group(0));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String expand(String hex) {
        String h = hex.length() == 3
                ? "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2)
                : hex;
        return h.toUpperCase();
    }

    private static int luminance(String rrggbb) {
        int r = Integer.parseInt(rrggbb.substring(0, 2), 16);
        int g = Integer.parseInt(rrggbb.substring(2, 4), 16);
        int b = Integer.parseInt(rrggbb.substring(4, 6), 16);
        return (int) (0.299 * r + 0.587 * g + 0.114 * b);
    }
}
