package com.cooked.backend.service.impl;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Reads a competitor's sitemap(s): how many pages, how many look like
 * recipes, and which recipes were added or updated in the last 7 days.
 * Only public addresses are fetched (no internal network), redirects are
 * checked hop by hop and bodies are capped.
 */
@Component
public class CompetitorCrawler {

    static final int MAX_SITEMAPS = 12;
    static final int MAX_BYTES = 15 * 1024 * 1024;
    static final int MAX_REDIRECTS = 5;
    private static final String UA = "CookedBot/1.0 (+https://cookedapp.com) sitemap reader";

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public record Result(int pages, int recipePages, int newThisWeek, List<String> latestTitles) {
    }

    record Entry(String loc, LocalDate lastmod) {
    }

    public Result crawl(String origin) throws IOException {
        List<String> queue = new ArrayList<>(sitemapsFromRobots(origin));
        if (queue.isEmpty()) queue.addAll(List.of(origin + "/sitemap.xml", origin + "/sitemap_index.xml"));
        Set<String> seen = new HashSet<>();
        List<Entry> urls = new ArrayList<>();
        int fetched = 0;
        boolean anyOk = false;
        while (!queue.isEmpty() && fetched < MAX_SITEMAPS) {
            String sm = queue.remove(0);
            if (!seen.add(sm)) continue;
            fetched++;
            String body;
            try {
                body = get(sm);
            } catch (IOException e) {
                continue;
            }
            anyOk = true;
            Document doc = Jsoup.parse(body, sm, Parser.xmlParser());
            List<String> children = doc.select("sitemap > loc").eachText();
            if (!children.isEmpty()) {
                // Recipe sitemaps first, newest last segments first.
                children.sort(Comparator.comparing((String c) -> !c.toLowerCase(Locale.ROOT).contains("recipe")).thenComparing(Comparator.reverseOrder()));
                queue.addAll(0, children);
                continue;
            }
            for (Element u : doc.select("url")) {
                String loc = u.selectFirst("loc") == null ? null : u.selectFirst("loc").text().trim();
                if (loc == null || loc.isEmpty()) continue;
                Element lm = u.selectFirst("lastmod");
                urls.add(new Entry(loc, lm == null ? null : day(lm.text())));
            }
        }
        if (!anyOk) throw new IOException("No sitemap found (robots.txt, /sitemap.xml)");
        LocalDate weekAgo = LocalDate.now().minusDays(7);
        List<Entry> recipes = urls.stream().filter(e -> looksLikeRecipe(e.loc())).toList();
        int fresh = (int) recipes.stream().filter(e -> e.lastmod() != null && !e.lastmod().isBefore(weekAgo)).count();
        List<String> latest = recipes.stream().filter(e -> e.lastmod() != null)
                .sorted(Comparator.comparing(Entry::lastmod).reversed())
                .map(e -> title(e.loc())).filter(Objects::nonNull).distinct().limit(5).toList();
        return new Result(urls.size(), recipes.size(), fresh, latest);
    }

    private List<String> sitemapsFromRobots(String origin) {
        try {
            List<String> out = new ArrayList<>();
            for (String line : get(origin + "/robots.txt").split("\\r?\\n")) {
                String l = line.trim();
                if (l.toLowerCase(Locale.ROOT).startsWith("sitemap:")) out.add(l.substring(8).trim());
            }
            return out;
        } catch (IOException e) {
            return List.of();
        }
    }

    static boolean looksLikeRecipe(String loc) {
        String l = loc.toLowerCase(Locale.ROOT);
        return l.contains("/recipe") || l.contains("/recette") || l.contains("/receta") || l.contains("-recipe");
    }

    /** "…/recipes/12345/creamy-garlic-pasta/" → "Creamy garlic pasta". */
    static String title(String loc) {
        try {
            String[] parts = URI.create(loc).getPath().split("/");
            for (int i = parts.length - 1; i >= 0; i--) {
                String p = parts[i].replaceAll("\\.(html?|php)$", "");
                if (p.isBlank() || p.matches("\\d+") || p.equalsIgnoreCase("recipe") || p.equalsIgnoreCase("recipes")) continue;
                String t = p.replaceAll("-\\d+$", "").replace('-', ' ').replace('_', ' ').trim();
                if (t.length() < 3) continue;
                return Character.toUpperCase(t.charAt(0)) + t.substring(1);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static LocalDate day(String text) {
        try {
            return LocalDate.parse(text.trim().substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }

    /** GET with public-address checks on every redirect hop and a body cap. */
    String get(String url) throws IOException {
        URI uri = URI.create(url);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            assertPublic(uri);
            HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).header("User-Agent", UA)
                    .header("Accept-Encoding", "gzip").GET().build();
            HttpResponse<InputStream> res;
            try {
                res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted");
            }
            int code = res.statusCode();
            if (code >= 300 && code < 400) {
                res.body().close();
                String location = res.headers().firstValue("Location").orElseThrow(() -> new IOException("Redirect without location"));
                uri = uri.resolve(location);
                continue;
            }
            try (InputStream raw = res.body()) {
                if (code != 200) throw new IOException("HTTP " + code + " for " + uri);
                byte[] bytes = raw.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) throw new IOException("Too large: " + uri);
                boolean gz = res.headers().firstValue("Content-Encoding").map(v -> v.contains("gzip")).orElse(false)
                        || uri.getPath().endsWith(".gz");
                if (gz) {
                    try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                        bytes = in.readNBytes(MAX_BYTES + 1);
                        if (bytes.length > MAX_BYTES) throw new IOException("Too large: " + uri);
                    }
                }
                return new String(bytes, StandardCharsets.UTF_8);
            }
        }
        throw new IOException("Too many redirects");
    }

    /** Refuses non-http(s) URLs and hosts that resolve to private, loopback or link-local addresses. */
    static void assertPublic(URI uri) throws IOException {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) throw new IOException("Only http(s) addresses are allowed");
        String host = uri.getHost();
        if (host == null || host.isBlank()) throw new IOException("Missing host");
        for (InetAddress a : InetAddress.getAllByName(host)) {
            byte[] b = a.getAddress();
            boolean cgnat = b.length == 4 && (b[0] & 0xFF) == 100 && (b[1] & 0xC0) == 64;
            boolean uniqueLocal6 = b.length == 16 && (b[0] & 0xFE) == 0xFC;
            if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                    || a.isMulticastAddress() || cgnat || uniqueLocal6) {
                throw new IOException("This address is not public");
            }
        }
    }
}
