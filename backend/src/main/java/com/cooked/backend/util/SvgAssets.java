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

    /**
     * The art's main colour: colours are grouped by hue (outlines, greys and highlights left out), the family
     * with the most saturated paint wins, and its mid-tone stands for it.
     */
    public static Optional<String> mainColor(String svg) {
        return family(svg).flatMap(f -> f.stream().min(Comparator.comparingDouble((Hsl c) -> Math.abs(c.l - 0.45))
                .thenComparing(c -> -c.score)).map(c -> "#" + c.hex));
    }

    /**
     * Moves the main colour family to {@code color} (#RRGGBB): every shade keeps its offset in lightness and
     * saturation from the main colour, so gradients stay gradients. Unchanged when the art has no main colour.
     */
    public static String recolor(String svg, String color) {
        if (color == null || !color.matches("#[0-9a-fA-F]{6}")) return svg;
        Optional<List<Hsl>> fam = family(svg);
        Optional<String> main = mainColor(svg);
        if (fam.isEmpty() || main.isEmpty()) return svg;
        Hsl m = hsl(main.get().substring(1), 0);
        Hsl t = hsl(color.substring(1).toUpperCase(Locale.ROOT), 0);
        Map<String, String> to = new HashMap<>();
        for (Hsl c : fam.get()) {
            if (c.hex.equals(m.hex)) {
                to.put(c.hex, t.hex);
                continue;
            }
            double s = clamp(c.s * (t.s / m.s), 0, 1);
            double l = clamp(c.l + (t.l - m.l), 0.03, 0.97);
            to.put(c.hex, rgb(t.h, s, l));
        }
        Matcher x = HEX.matcher(svg);
        StringBuilder sb = new StringBuilder();
        while (x.find()) {
            String next = to.get(expand(x.group(1)));
            x.appendReplacement(sb, next == null ? x.group(0) : "#" + next);
        }
        x.appendTail(sb);
        return sb.toString();
    }

    record Hsl(String hex, double h, double s, double l, double score) {
    }

    /** Colours of the dominant hue family (within 30° of the best 30° bucket, vivid enough), with their weight. */
    private static Optional<List<Hsl>> family(String svg) {
        Map<String, Integer> counts = new HashMap<>();
        Matcher m = HEX.matcher(svg);
        while (m.find()) counts.merge(expand(m.group(1)), 1, Integer::sum);
        List<Hsl> paint = new ArrayList<>();
        counts.forEach((hex, n) -> {
            Hsl c = hsl(hex, 0);
            if (c.s >= 0.25 && c.l >= 0.15 && c.l <= 0.92) paint.add(new Hsl(hex, c.h, c.s, c.l, n * c.s));
        });
        if (paint.isEmpty()) return Optional.empty();
        double[] buckets = new double[12];
        paint.forEach(c -> buckets[(int) (c.h / 30) % 12] += c.score);
        int best = 0;
        for (int i = 1; i < 12; i++) if (buckets[i] > buckets[best]) best = i;
        double center = best * 30 + 15;
        List<Hsl> near = paint.stream().filter(c -> hueDistance(c.h, center) <= 30).toList();
        // dull shades of the same hue (a brown stem next to a red body) are not part of the paint
        double minSat = 0.6 * near.stream().mapToDouble(Hsl::s).max().orElse(0);
        List<Hsl> fam = near.stream().filter(c -> c.s >= minSat).toList();
        return fam.isEmpty() ? Optional.empty() : Optional.of(fam);
    }

    private static double hueDistance(double a, double b) {
        double d = Math.abs(a - b) % 360;
        return d > 180 ? 360 - d : d;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static Hsl hsl(String rrggbb, double score) {
        double r = Integer.parseInt(rrggbb.substring(0, 2), 16) / 255.0;
        double g = Integer.parseInt(rrggbb.substring(2, 4), 16) / 255.0;
        double b = Integer.parseInt(rrggbb.substring(4, 6), 16) / 255.0;
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        double l = (max + min) / 2, h = 0, s = 0;
        if (max != min) {
            double d = max - min;
            s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
            if (max == r) h = ((g - b) / d + (g < b ? 6 : 0)) * 60;
            else if (max == g) h = ((b - r) / d + 2) * 60;
            else h = ((r - g) / d + 4) * 60;
        }
        return new Hsl(rrggbb, h, s, l, score);
    }

    private static String rgb(double h, double s, double l) {
        double c = (1 - Math.abs(2 * l - 1)) * s, x = c * (1 - Math.abs((h / 60) % 2 - 1)), m = l - c / 2;
        double[] p = h < 60 ? new double[]{c, x, 0} : h < 120 ? new double[]{x, c, 0} : h < 180 ? new double[]{0, c, x}
                : h < 240 ? new double[]{0, x, c} : h < 300 ? new double[]{x, 0, c} : new double[]{c, 0, x};
        return String.format(Locale.ROOT, "%02X%02X%02X", Math.round((p[0] + m) * 255), Math.round((p[1] + m) * 255), Math.round((p[2] + m) * 255));
    }

    private static String expand(String hex) {
        String h = hex.length() == 3
                ? "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2)
                : hex;
        return h.toUpperCase();
    }

}
