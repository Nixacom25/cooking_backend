package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.AskCookedResponse;
import com.cooked.backend.entity.IntegrationKey;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.service.AskCookedService;
import com.cooked.backend.service.IntegrationEventRecorder;
import com.cooked.backend.service.askcooked.AskCookedTool;
import com.cooked.backend.service.askcooked.ChatCompletionClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Function-calling loop: the model picks read-only tools, we run them and send
 * the JSON back until it answers. Never writes, never sees customers' personal data.
 */
@Slf4j
@Service
public class AskCookedServiceImpl implements AskCookedService {

    static final int MAX_ROUNDS = 4;
    static final int MAX_TOOL_CHARS = 14_000;
    static final int QUESTIONS_PER_HOUR = 30;

    private final ChatCompletionClient chat;
    private final Map<String, AskCookedTool> tools = new LinkedHashMap<>();
    private final ObjectMapper objectMapper;
    private final IntegrationEventRecorder integrationEvents;
    private final String model;
    private final Clock clock;
    private final Map<String, Deque<Instant>> recent = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public AskCookedServiceImpl(ChatCompletionClient chat, List<AskCookedTool> tools, ObjectMapper objectMapper,
                                IntegrationEventRecorder integrationEvents, @Value("${ask-cooked.model:gpt-4o-mini}") String model) {
        this(chat, tools, objectMapper, integrationEvents, model, Clock.systemUTC());
    }

    AskCookedServiceImpl(ChatCompletionClient chat, List<AskCookedTool> tools, ObjectMapper objectMapper,
                         IntegrationEventRecorder integrationEvents, String model, Clock clock) {
        this.chat = chat;
        tools.forEach(t -> this.tools.put(t.name(), t));
        this.objectMapper = objectMapper;
        this.integrationEvents = integrationEvents;
        this.model = model;
        this.clock = clock;
    }

    @Override
    public AskCookedResponse ask(String question, String adminEmail) {
        String q = question == null ? "" : question.trim();
        if (q.isEmpty() || q.length() > 500) throw new BadRequestException("Ask a question of 1 to 500 characters.");
        if (!chat.isConfigured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Ask Cooked needs OPENAI_API_KEY on the server.");
        throttle(adminEmail);

        long start = System.nanoTime();
        Set<String> sources = new LinkedHashSet<>();
        try {
            ArrayNode messages = objectMapper.createArrayNode();
            messages.add(message("system", systemPrompt()));
            messages.add(message("user", q));
            for (int round = 0; round < MAX_ROUNDS; round++) {
                JsonNode reply = chat.complete(request(messages, round < MAX_ROUNDS - 1)).path("choices").path(0).path("message");
                JsonNode calls = reply.path("tool_calls");
                if (!calls.isArray() || calls.isEmpty()) {
                    String answer = reply.path("content").asText("").trim();
                    record(true, start, null);
                    return AskCookedResponse.builder().question(q)
                            .answer(answer.isEmpty() ? "I couldn't find an answer in Cooked's data." : answer)
                            .sources(new ArrayList<>(sources)).tookMs(elapsed(start)).model(model).build();
                }
                messages.add(reply);
                for (JsonNode call : calls) {
                    String name = call.path("function").path("name").asText();
                    messages.add(toolResult(call.path("id").asText(), runTool(name, call.path("function").path("arguments").asText("{}"), sources)));
                }
            }
            throw new IllegalStateException("No answer after " + MAX_ROUNDS + " rounds");
        } catch (ResponseStatusException | BadRequestException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Ask Cooked failed: {}", e.getMessage());
            record(false, start, e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Ask Cooked couldn't answer right now. Try again in a moment.");
        }
    }

    String runTool(String name, String rawArgs, Set<String> sources) {
        AskCookedTool tool = tools.get(name);
        if (tool == null) return "{\"error\":\"unknown tool\"}";
        try {
            Object data = tool.run(objectMapper.readTree(rawArgs == null || rawArgs.isBlank() ? "{}" : rawArgs));
            sources.add(tool.sourceLabel());
            String json = objectMapper.writeValueAsString(data);
            return json.length() <= MAX_TOOL_CHARS ? json : json.substring(0, MAX_TOOL_CHARS) + "…(truncated)";
        } catch (Exception e) {
            log.warn("Ask Cooked tool {} failed: {}", name, e.getMessage());
            return "{\"error\":\"data unavailable\"}";
        }
    }

    /** At most {@value #QUESTIONS_PER_HOUR} questions per admin per hour (OpenAI cost guard). */
    void throttle(String adminEmail) {
        Instant now = clock.instant();
        Deque<Instant> q = recent.computeIfAbsent(adminEmail == null ? "?" : adminEmail, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst().isBefore(now.minusSeconds(3600))) q.pollFirst();
            if (q.size() >= QUESTIONS_PER_HOUR) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Question limit reached (" + QUESTIONS_PER_HOUR + " per hour).");
            }
            q.addLast(now);
        }
    }

    private ObjectNode request(ArrayNode messages, boolean allowTools) {
        ObjectNode req = objectMapper.createObjectNode();
        req.put("model", model);
        req.put("temperature", 0.2);
        req.set("messages", messages);
        if (allowTools) {
            ArrayNode specs = req.putArray("tools");
            for (AskCookedTool t : tools.values()) {
                ObjectNode fn = objectMapper.createObjectNode();
                fn.put("name", t.name());
                fn.put("description", t.description());
                fn.set("parameters", objectMapper.valueToTree(t.parameters()));
                specs.addObject().put("type", "function").set("function", fn);
            }
        }
        return req;
    }

    private String systemPrompt() {
        return "You are Ask Cooked, the read-only analyst of the Cooked admin backoffice (Cooked is a recipe app: scan ingredients, import recipes, search the web). "
                + "Today is " + LocalDate.now(clock) + ". Answer only from the tools' data; call the tools you need, never invent or estimate numbers. "
                + "If the data needed is not available, say so plainly and say what is measured instead. "
                + "Be concise: a one-sentence answer first, then up to 5 short bullet points with the key figures and the period they cover. "
                + "Money: costs are in USD, revenue in the currency returned. Answer in the language of the question. "
                + "You cannot change anything in Cooked.";
    }

    private ObjectNode message(String role, String content) {
        ObjectNode m = objectMapper.createObjectNode();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private ObjectNode toolResult(String id, String content) {
        ObjectNode m = message("tool", content);
        m.put("tool_call_id", id);
        return m;
    }

    private void record(boolean ok, long start, String error) {
        integrationEvents.record(IntegrationKey.OPENAI, "ask_cooked", ok, ok ? 200 : null, (int) elapsed(start), error);
    }

    private static long elapsed(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
