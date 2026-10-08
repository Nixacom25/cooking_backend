package com.cooked.backend.util;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * Normalized form of an ingredient name, used to match names, canonical ids and aliases:
 * case, accent and simple-plural insensitive ("Nététou" = "netetou", "Green Onions" = "green_onion").
 */
public final class IngredientKeys {

    public static final int MAX_LENGTH = 120;

    private IngredientKeys() {
    }

    public static String key(String name) {
        if (name == null) return "";
        String s = Normalizer.normalize(name.trim().toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String[] tokens = s.split("[^a-z0-9]+");
        List<String> out = new ArrayList<>();
        for (String t : tokens) if (!t.isEmpty()) out.add(singular(t));
        String k = String.join("_", out);
        return k.length() > MAX_LENGTH ? k.substring(0, MAX_LENGTH) : k;
    }

    static String singular(String t) {
        if (t.length() <= 3 || !t.endsWith("s")) return t;
        if (t.endsWith("ss") || t.endsWith("us") || t.endsWith("is")) return t;
        if (t.endsWith("ies")) return t.substring(0, t.length() - 3) + "y";
        if (t.endsWith("oes") || t.endsWith("ches") || t.endsWith("shes") || t.endsWith("xes") || t.endsWith("ses") || t.endsWith("zes")) {
            return t.substring(0, t.length() - 2);
        }
        return t.substring(0, t.length() - 1);
    }

    /** Valid permanent id: lower snake_case, starts with a letter. */
    public static boolean isCanonicalId(String id) {
        return id != null && id.matches("[a-z][a-z0-9]*(_[a-z0-9]+)*") && id.length() <= 80;
    }

    /** 0..1 similarity of two keys: edit distance, boosted when every word of one appears in the other. */
    public static double similarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        if (a.equals(b)) return 1;
        int max = Math.max(a.length(), b.length());
        double edit = 1.0 - (double) levenshtein(a, b) / max;
        List<String> ta = List.of(a.split("_")), tb = List.of(b.split("_"));
        boolean contained = tb.containsAll(ta) || ta.containsAll(tb);
        return contained ? Math.max(edit, 0.8) : edit;
    }

    static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }
}
