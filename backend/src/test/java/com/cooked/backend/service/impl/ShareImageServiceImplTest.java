package com.cooked.backend.service.impl;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShareImageServiceImplTest {

    private static BufferedImage solid(Color color, int w, int h) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return image;
    }

    @Test
    void render_putsTheFourImagesInA2x2Grid() {
        BufferedImage canvas = ShareImageServiceImpl.render(List.of(
                solid(Color.RED, 300, 300),
                solid(Color.GREEN, 800, 200),
                solid(Color.BLUE, 100, 900),
                solid(Color.YELLOW, 640, 480)));

        assertThat(canvas.getWidth()).isEqualTo(ShareImageServiceImpl.WIDTH);
        assertThat(canvas.getHeight()).isEqualTo(ShareImageServiceImpl.HEIGHT);
        assertThat(new Color(canvas.getRGB(10, 10))).isEqualTo(Color.RED);
        assertThat(new Color(canvas.getRGB(1190, 10))).isEqualTo(Color.GREEN);
        assertThat(new Color(canvas.getRGB(10, 620))).isEqualTo(Color.BLUE);
        assertThat(new Color(canvas.getRGB(1190, 620))).isEqualTo(Color.YELLOW);
        // White gutter between the tiles.
        assertThat(new Color(canvas.getRGB(599, 100))).isEqualTo(Color.WHITE);
    }

    @Test
    void collage_isEmptyWithFewerThanFourImages() {
        assertThat(new ShareImageServiceImpl().collage(List.of("https://a/1.jpg", "https://a/2.jpg"))).isEmpty();
    }

    @Test
    void collage_skipsUrlsThatAreNotPublicHttps() {
        assertThat(new ShareImageServiceImpl().collage(List.of(
                "http://example.com/1.jpg", "https://127.0.0.1/2.jpg",
                "https://localhost/3.jpg", "file:///etc/passwd"))).isEmpty();
    }

    @Test
    void tileUrl_asksCloudinaryForASmallJpeg() {
        assertThat(ShareImageServiceImpl.tileUrl("https://res.cloudinary.com/x/image/upload/v1/a.webp"))
                .isEqualTo("https://res.cloudinary.com/x/image/upload/w_600,h_315,c_fill,q_auto,f_jpg/v1/a.webp");
        assertThat(ShareImageServiceImpl.tileUrl("https://cdn.example.com/a.jpg"))
                .isEqualTo("https://cdn.example.com/a.jpg");
    }
}
