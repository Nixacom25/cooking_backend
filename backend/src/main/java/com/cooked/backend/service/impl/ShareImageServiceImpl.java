package com.cooked.backend.service.impl;

import com.cooked.backend.service.ShareImageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class ShareImageServiceImpl implements ShareImageService {

    static final int WIDTH = 1200;
    static final int HEIGHT = 630;
    /** White gutter between tiles, like the app's cookbook covers. */
    static final int GAP = 4;
    /** Extra images tried when some of the first ones can't be decoded (e.g. WebP). */
    private static final int MAX_CANDIDATES = COLLAGE_SIZE * 2;
    private static final int MAX_DOWNLOAD_BYTES = 8 * 1024 * 1024;
    private static final int CACHE_SIZE = 200;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            // A redirect could lead past the public-host check below.
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /** Rendered collages by image list: crawlers fetch the same link many times. */
    private final Map<List<String>, byte[]> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<List<String>, byte[]> eldest) {
                    return size() > CACHE_SIZE;
                }
            });

    @Override
    public Optional<byte[]> collage(List<String> imageUrls) {
        List<String> candidates = imageUrls.stream().limit(MAX_CANDIDATES).toList();
        if (candidates.size() < COLLAGE_SIZE) return Optional.empty();

        byte[] cached = cache.get(candidates);
        if (cached != null) return Optional.of(cached);

        List<CompletableFuture<BufferedImage>> downloads = candidates.stream()
                .map(url -> CompletableFuture.supplyAsync(() -> download(url)))
                .toList();
        List<BufferedImage> tiles = new ArrayList<>();
        for (CompletableFuture<BufferedImage> download : downloads) {
            BufferedImage image = download.join();
            if (image != null) tiles.add(image);
            if (tiles.size() == COLLAGE_SIZE) break;
        }
        if (tiles.size() < COLLAGE_SIZE) return Optional.empty();

        try {
            byte[] jpeg = toJpeg(render(tiles));
            cache.put(candidates, jpeg);
            return Optional.of(jpeg);
        } catch (IOException e) {
            log.warn("Could not encode cookbook collage: {}", e.getMessage());
            return Optional.empty();
        }
    }

    static BufferedImage render(List<BufferedImage> tiles) {
        BufferedImage canvas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, WIDTH, HEIGHT);

            int tileW = (WIDTH - GAP) / 2;
            int tileH = (HEIGHT - GAP) / 2;
            for (int i = 0; i < COLLAGE_SIZE; i++) {
                int x = (i % 2) * (tileW + GAP);
                int y = (i / 2) * (tileH + GAP);
                drawCover(g, tiles.get(i), x, y, tileW, tileH);
            }
        } finally {
            g.dispose();
        }
        return canvas;
    }

    /** Scales [image] to fill the box and crops the overflow (CSS object-fit: cover). */
    private static void drawCover(Graphics2D g, BufferedImage image, int x, int y, int w, int h) {
        double scale = Math.max((double) w / image.getWidth(), (double) h / image.getHeight());
        int srcW = (int) Math.round(w / scale);
        int srcH = (int) Math.round(h / scale);
        int srcX = (image.getWidth() - srcW) / 2;
        int srcY = (image.getHeight() - srcH) / 2;
        g.drawImage(image, x, y, x + w, y + h, srcX, srcY, srcX + srcW, srcY + srcH, null);
    }

    private BufferedImage download(String url) {
        try {
            URI uri = URI.create(tileUrl(url));
            if (!isPublicHttps(uri)) return null;
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) return null;
                byte[] bytes = body.readNBytes(MAX_DOWNLOAD_BYTES + 1);
                if (bytes.length > MAX_DOWNLOAD_BYTES) return null;
                return ImageIO.read(new ByteArrayInputStream(bytes));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.debug("Skipping collage image {}: {}", url, e.getMessage());
            return null;
        }
    }

    /** Cloudinary serves a small JPEG of the tile size (ImageIO can't read WebP). */
    static String tileUrl(String url) {
        if (url.contains("res.cloudinary.com") && url.contains("/image/upload/")) {
            return url.replaceFirst("/image/upload/", "/image/upload/w_600,h_315,c_fill,q_auto,f_jpg/");
        }
        return url;
    }

    /** Image URLs come from user content: never reach internal hosts. */
    private static boolean isPublicHttps(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) return false;
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                        || address.isAnyLocalAddress() || address.isMulticastAddress()) {
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static byte[] toJpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            // Keeps the image well under WhatsApp's ~300 KB preview limit.
            param.setCompressionQuality(0.8f);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
