package com.cooked.backend.service.impl;

import com.cooked.backend.service.AiImageGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiImageGeneratorTest {

    private static final String IMAGE = "{\"data\":[{\"b64_json\":\"" + Base64.getEncoder().encodeToString(new byte[]{7, 8}) + "\"}]}";

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<JsonNode> requests = new ArrayList<>();
    private HttpServer server;
    private Function<JsonNode, Reply> handler;

    private record Reply(int status, String body) {
    }

    private static Reply error(int status, String message) {
        return new Reply(status, "{\"error\":{\"message\":\"" + message + "\"}}");
    }

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/images", exchange -> {
            JsonNode body = mapper.readTree(exchange.getRequestBody());
            requests.add(body);
            Reply reply = handler.apply(body);
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(reply.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private OpenAiImageGenerator generator(String model, String fallbacks) {
        return new OpenAiImageGenerator(mapper, "http://127.0.0.1:" + server.getAddress().getPort() + "/images",
                "key", model, "medium", "1024x1024", 0.034, fallbacks, 0.05);
    }

    @Test
    void generate_movesOnFromARetiredModel() throws IOException {
        handler = body -> body.path("model").asText().equals("gpt-image-1")
                ? error(400, "The model 'gpt-image-1' does not exist.")
                : new Reply(200, IMAGE);

        AiImageGenerator.GeneratedImage image = generator("gpt-image-1", "gpt-image-1.5,gpt-image-2").generate("toast");

        assertThat(image.model()).isEqualTo("gpt-image-1.5");
        assertThat(image.bytes()).containsExactly(7, 8);
        assertThat(image.costUsd()).isEqualTo(0.05);
    }

    @Test
    void generate_dropsAParameterTheApiNoLongerAccepts() throws IOException {
        handler = body -> body.has("output_compression")
                ? error(400, "Unknown parameter: 'output_compression'.")
                : new Reply(200, IMAGE);

        AiImageGenerator.GeneratedImage image = generator("gpt-image-1.5", "").generate("toast");

        assertThat(image.model()).isEqualTo("gpt-image-1.5");
        assertThat(image.costUsd()).isEqualTo(0.034);
        assertThat(requests).hasSize(2);
        assertThat(requests.get(1).has("output_compression")).isFalse();
        assertThat(requests.get(1).path("quality").asText()).isEqualTo("medium");
    }

    @Test
    void generate_doesNotRetryOtherModelsWhenThePromptIsRefused() {
        handler = body -> error(400, "Your request was rejected as a result of our safety system.");

        assertThatThrownBy(() -> generator("gpt-image-1.5", "gpt-image-2").generate("toast"))
                .isInstanceOf(OpenAiImageGenerator.Refused.class);
        assertThat(requests).hasSize(1);
    }

    @Test
    void generate_failsOnlyWhenEveryModelFails() {
        handler = body -> error(500, "server error");

        assertThatThrownBy(() -> generator("gpt-image-1.5", "gpt-image-2").generate("toast"))
                .hasMessageContaining("gpt-image-2");
        assertThat(requests).extracting(r -> r.path("model").asText()).containsExactly("gpt-image-1.5", "gpt-image-2");
    }
}
