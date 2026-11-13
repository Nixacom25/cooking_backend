package com.cooked.backend.service.impl;

import com.cooked.backend.service.AiImageGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FalImageGeneratorTest {

    private HttpServer server;
    private String base;
    private final AtomicReference<String> auth = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private volatile int status = 200;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        server.createContext("/fal-ai/flux-2/klein/9b", ex -> {
            auth.set(ex.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = (status == 200
                    ? "{\"images\":[{\"url\":\"" + base + "files/a.png\",\"width\":1024,\"height\":1024}]}"
                    : "{\"detail\":\"User is locked. Reason: TOP_UP.\"}").getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.createContext("/files/a.png", ex -> {
            byte[] png = {(byte) 0x89, 'P', 'N', 'G'};
            ex.sendResponseHeaders(200, png.length);
            ex.getResponseBody().write(png);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private FalImageGenerator gen(String key) {
        return new FalImageGenerator(new ObjectMapper(), base, key, "fal-ai/flux-2/klein/9b", 1024, 0.006);
    }

    @Test
    void generatesDownloadsAndPricesThePhoto() throws IOException {
        AiImageGenerator.GeneratedImage img = gen("id:secret").generate("Chicken yassa");
        assertThat(img.bytes()).containsExactly((byte) 0x89, 'P', 'N', 'G');
        assertThat(img.model()).isEqualTo("fal-ai/flux-2/klein/9b");
        assertThat(img.costUsd()).isEqualTo(0.0063);
        assertThat(auth.get()).isEqualTo("Key id:secret");
        assertThat(body.get()).contains("\"prompt\":\"Chicken yassa\"").contains("\"width\":1024");
    }

    @Test
    void reportsProviderErrorsAndAMissingKey() {
        status = 403;
        assertThatThrownBy(() -> gen("id:secret").generate("x")).hasMessageContaining("HTTP 403").hasMessageContaining("TOP_UP");
        assertThat(gen(" ").available()).isFalse();
        assertThatThrownBy(() -> gen("").generate("x")).hasMessageContaining("FAL_KEY");
    }
}
