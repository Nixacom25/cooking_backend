package com.cooked.backend.service;

import java.io.IOException;

/** Text-to-image provider for recipe photos: OpenAI for creators, fal (FLUX.2 Klein) for clients. */
public interface AiImageGenerator {

    /**
     * One generated photo, with the model that actually made it and its estimated cost.
     * [sourceUrl] is where the provider already serves it (fal), or null.
     */
    record GeneratedImage(byte[] bytes, String model, double costUsd, String sourceUrl) {
        public GeneratedImage(byte[] bytes, String model, double costUsd) {
            this(bytes, model, costUsd, null);
        }
    }

    /** Provider name stored with each photo, e.g. "openai". */
    String provider();

    /** Preferred model (the one tried first). */
    String model();

    String quality();

    /** False when the provider can't be called (no API key configured). */
    default boolean available() {
        return true;
    }

    /** Generates one image for [prompt]; throws when the provider refuses or fails. */
    GeneratedImage generate(String prompt) throws IOException;
}
