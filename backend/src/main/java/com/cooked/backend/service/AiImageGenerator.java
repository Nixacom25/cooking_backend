package com.cooked.backend.service;

import java.io.IOException;

/** Text-to-image provider for creator recipe photos (OpenAI today). */
public interface AiImageGenerator {

    /** One generated photo, with the model that actually made it and its estimated cost. */
    record GeneratedImage(byte[] bytes, String model, double costUsd) {
    }

    /** Provider name stored with each photo, e.g. "openai". */
    String provider();

    /** Preferred model (the one tried first). */
    String model();

    String quality();

    /** Generates one image for [prompt]; throws when the provider refuses or fails. */
    GeneratedImage generate(String prompt) throws IOException;
}
