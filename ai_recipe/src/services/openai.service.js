'use strict';

const fs = require('fs').promises;
const path = require('path');
const OpenAI = require('openai');
const { AppError } = require('../middleware/errorHandler');

const config = require('../config');

const DEFAULT_OPENAI_MODEL = config.openai.generationModel;
const PROMPT_TEMPLATE_PATH = path.resolve(process.cwd(), "prompt.md");

let cachedClient = null;
let cachedPromptTemplate = null;

/**
 * Lazily initialises the OpenAI client.
 */
const getOpenAIClient = () => {
    if (cachedClient) return cachedClient;

    const apiKey = process.env.OPENAI_API_KEY;
    if (!apiKey) {
        throw new AppError(500, 'AI_SERVICE_ERROR', "Missing OPENAI_API_KEY in environment variables");
    }

    cachedClient = new OpenAI({ apiKey });
    return cachedClient;
};

/**
 * Loads and caches the prompt template.
 */
const getPromptTemplate = async () => {
    if (cachedPromptTemplate && process.env.NODE_ENV === 'production') return cachedPromptTemplate;

    try {
        cachedPromptTemplate = await fs.readFile(PROMPT_TEMPLATE_PATH, "utf8");
    } catch (err) {
        throw new AppError(500, 'AI_SERVICE_ERROR', "Unable to load prompt.md template for extraction service");
    }

    return cachedPromptTemplate;
};

const buildPrompt = (template, captionText) => {
    return template.replace("{{INSERT_CAPTION_HERE}}", captionText.trim());
};

const parseModelJson = (rawText) => {
    const cleaned = rawText
        .replace(/^```json\s*/i, "")
        .replace(/^```\s*/i, "")
        .replace(/\s*```$/, "")
        .trim();

    try {
        return JSON.parse(cleaned);
    } catch (err) {
        console.error("JSON Parsing Error. Raw text snippet:", rawText.substring(0, 500));
        throw new AppError(502, 'SCHEMA_VALIDATION_FAILED', "AI returned invalid JSON during extraction");
    }
};

/**
 * Main function to extract a recipe from a caption/description text.
 * 
 * @param {string} captionText 
 * @returns {Promise<object>} Parsed recipe
 */
const extractRecipeFromCaption = async (captionText) => {
    if (!captionText || typeof captionText !== "string") {
        throw new AppError(400, 'BAD_REQUEST', "captionText must be a non-empty string");
    }

    const client = getOpenAIClient();
    const template = await getPromptTemplate();
    const prompt = buildPrompt(template, captionText);

    try {
        const response = await client.chat.completions.create({
            model: DEFAULT_OPENAI_MODEL,
            messages: [
                { role: 'user', content: prompt }
            ],
            response_format: { type: 'json_object' },
            max_tokens: 3000,
            temperature: 0, // More deterministic for structured data
        });

        const rawText = response.choices[0]?.message?.content;
        if (!rawText) {
            throw new Error("Empty response from OpenAI");
        }

        return parseModelJson(rawText);
    } catch (err) {
        if (err instanceof AppError) throw err;
        throw new AppError(502, 'AI_SERVICE_ERROR', `Failed to extract recipe via OpenAI: ${err.message}`);
    }
};

module.exports = {
    openaiService: {
        extractRecipeFromCaption,
    }
};
