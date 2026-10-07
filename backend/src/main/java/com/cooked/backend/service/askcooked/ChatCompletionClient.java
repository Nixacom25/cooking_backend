package com.cooked.backend.service.askcooked;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Sends one chat-completions request (OpenAI format) and returns the raw response. */
public interface ChatCompletionClient {

    boolean isConfigured();

    JsonNode complete(ObjectNode request);
}
