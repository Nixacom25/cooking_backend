package com.cooked.backend.service.impl;

import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.service.IntegrationEventRecorder;
import com.cooked.backend.service.askcooked.AskCookedTool;
import com.cooked.backend.service.askcooked.ChatCompletionClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AskCookedServiceImplTest {

    private final ObjectMapper om = new ObjectMapper();

    /** Scripted model: first asks for a tool, then answers using what it got back. */
    static class FakeChat implements ChatCompletionClient {
        final List<ObjectNode> requests = new ArrayList<>();
        final ObjectMapper om = new ObjectMapper();
        boolean configured = true;

        public boolean isConfigured() { return configured; }

        public JsonNode complete(ObjectNode request) {
            requests.add(request.deepCopy());
            try {
                if (requests.size() == 1) {
                    return om.readTree("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"c1\",\"type\":\"function\","
                            + "\"function\":{\"name\":\"scans\",\"arguments\":\"{\\\"days\\\":7}\"}}]}}]}");
                }
                String toolJson = request.path("messages").get(request.path("messages").size() - 1).path("content").asText();
                return om.readTree("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Scans: " + om.readTree(toolJson).path("total").asInt() + "\"}}]}");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static AskCookedTool scans(List<Integer> seenDays) {
        return new AskCookedTool() {
            public String name() { return "scans"; }
            public String description() { return "scan totals"; }
            public String sourceLabel() { return "Product analytics"; }
            public Map<String, Object> parameters() { return DAYS_PARAMETERS; }
            public Object run(JsonNode a) { seenDays.add(AskCookedTool.days(a)); return Map.of("total", 42); }
        };
    }

    private AskCookedServiceImpl service(FakeChat chat, List<Integer> seen) {
        return new AskCookedServiceImpl(chat, List.of(scans(seen)), om, mock(IntegrationEventRecorder.class), "gpt-4o-mini",
                Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void runsToolsThenAnswers() {
        FakeChat chat = new FakeChat();
        List<Integer> seen = new ArrayList<>();
        var r = service(chat, seen).ask("How many scans this week?", "admin@cooked.app");

        assertEquals("Scans: 42", r.getAnswer());
        assertEquals(List.of("Product analytics"), r.getSources());
        assertEquals(List.of(7), seen);
        assertEquals("scans", chat.requests.get(0).path("tools").get(0).path("function").path("name").asText());
        assertTrue(chat.requests.get(0).path("messages").get(0).path("content").asText().contains("2026-10-07"));
    }

    @Test
    void validatesConfiguresAndThrottles() {
        FakeChat chat = new FakeChat();
        AskCookedServiceImpl s = service(chat, new ArrayList<>());
        assertThrows(BadRequestException.class, () -> s.ask("  ", "a"));
        assertThrows(BadRequestException.class, () -> s.ask("x".repeat(501), "a"));

        chat.configured = false;
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ResponseStatusException.class, () -> s.ask("hi", "a")).getStatusCode());

        for (int i = 0; i < AskCookedServiceImpl.QUESTIONS_PER_HOUR; i++) s.throttle("b");
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, assertThrows(ResponseStatusException.class, () -> s.throttle("b")).getStatusCode());
        s.throttle("someone-else");
    }

    @Test
    void unknownToolsAndFailuresStayInside() {
        AskCookedServiceImpl s = service(new FakeChat(), new ArrayList<>());
        assertTrue(s.runTool("drop_tables", "{}", new java.util.HashSet<>()).contains("unknown tool"));
        assertTrue(s.runTool("scans", "not json", new java.util.HashSet<>()).contains("data unavailable"));
    }
}
