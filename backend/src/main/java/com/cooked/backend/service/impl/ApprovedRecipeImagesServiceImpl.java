package com.cooked.backend.service.impl;

import com.cooked.backend.repository.RecipeAssignmentRepository;
import com.cooked.backend.repository.RecipeAssignmentRepository.ApprovedRecipeImage;
import com.cooked.backend.service.ApprovedRecipeImagesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
public class ApprovedRecipeImagesServiceImpl implements ApprovedRecipeImagesService {

    /** Downloads a photo; returns its bytes and content type. */
    public interface ImageFetcher {
        Fetched fetch(String url) throws Exception;
    }

    public record Fetched(byte[] bytes, String contentType) {}

    private static final int PARALLEL = 8;
    private static final long MAX_BYTES = 25L * 1024 * 1024;

    private final RecipeAssignmentRepository assignments;
    private final ImageFetcher fetcher;

    @Autowired
    public ApprovedRecipeImagesServiceImpl(RecipeAssignmentRepository assignments) {
        this(assignments, httpFetcher());
    }

    public ApprovedRecipeImagesServiceImpl(RecipeAssignmentRepository assignments, ImageFetcher fetcher) {
        this.assignments = assignments;
        this.fetcher = fetcher;
    }

    @Override
    public int countApprovedWithImage() {
        return assignments.findApprovedRecipeImages().size();
    }

    @Override
    public void writeZip(OutputStream out) throws IOException {
        List<ApprovedRecipeImage> rows = assignments.findApprovedRecipeImages().stream()
                .filter(r -> r.getImage() != null && r.getImage().startsWith("http"))
                .toList();
        ExecutorService pool = Executors.newFixedThreadPool(PARALLEL);
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            StringBuilder manifest = new StringBuilder("recipe_id,name,image_url,file\n");
            Set<String> used = new HashSet<>();
            // A sliding window of downloads keeps memory bounded while photos are written in order.
            List<CompletableFuture<Fetched>> window = new ArrayList<>();
            int next = 0;
            for (int i = 0; i < rows.size(); i++) {
                while (next < rows.size() && next < i + PARALLEL * 2) {
                    String url = rows.get(next++).getImage();
                    window.add(CompletableFuture.supplyAsync(() -> {
                        try { return fetcher.fetch(url); } catch (Exception e) { throw new IllegalStateException(e.getMessage(), e); }
                    }, pool));
                }
                ApprovedRecipeImage r = rows.get(i);
                String file;
                try {
                    Fetched f = window.get(i).join();
                    file = uniqueName(baseName(r), extension(f.contentType(), r.getImage()), used);
                    zip.putNextEntry(new ZipEntry("images/" + file));
                    zip.write(f.bytes());
                    zip.closeEntry();
                } catch (RuntimeException e) {
                    log.warn("[ApprovedImages] {} skipped: {}", r.getImage(), e.getMessage());
                    file = "MISSING (" + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()) + ")";
                }
                window.set(i, null);
                manifest.append(csv(String.valueOf(r.getId()))).append(',').append(csv(r.getName())).append(',')
                        .append(csv(r.getImage())).append(',').append(csv(file)).append('\n');
            }
            zip.putNextEntry(new ZipEntry("manifest.csv"));
            zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } finally {
            pool.shutdownNow();
        }
    }

    static String baseName(ApprovedRecipeImage r) {
        String name = r.getName() == null ? "" : Normalizer.normalize(r.getName(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (name.length() > 60) name = name.substring(0, 60).replaceAll("-$", "");
        String id = String.valueOf(r.getId()).replace("-", "");
        return (name.isEmpty() ? "recipe" : name) + "-" + id.substring(0, Math.min(8, id.length()));
    }

    static String extension(String contentType, String url) {
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (ct.contains("png")) return "png";
        if (ct.contains("webp")) return "webp";
        if (ct.contains("gif")) return "gif";
        if (ct.contains("avif")) return "avif";
        if (ct.contains("jpeg") || ct.contains("jpg")) return "jpg";
        String path = url.split("[?#]")[0].toLowerCase(Locale.ROOT);
        for (String e : List.of("png", "webp", "gif", "avif", "jpeg", "jpg")) {
            if (path.endsWith("." + e)) return e.equals("jpeg") ? "jpg" : e;
        }
        return "jpg";
    }

    private static String uniqueName(String base, String ext, Set<String> used) {
        String name = base + "." + ext;
        for (int n = 2; !used.add(name); n++) name = base + "-" + n + "." + ext;
        return name;
    }

    private static String csv(String v) {
        String s = v == null ? "" : v;
        return s.matches(".*[\",\\n\\r].*") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    private static ImageFetcher httpFetcher() {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        return url -> {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "CookedAdminExport/1.0").GET().build();
            HttpResponse<byte[]> res = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() / 100 != 2) throw new IOException("HTTP " + res.statusCode());
            String type = res.headers().firstValue("Content-Type").orElse("");
            if (!type.isEmpty() && !type.toLowerCase(Locale.ROOT).startsWith("image/")) throw new IOException("not an image (" + type + ")");
            if (res.body().length == 0 || res.body().length > MAX_BYTES) throw new IOException("empty or too large");
            return new Fetched(res.body(), type);
        };
    }
}
