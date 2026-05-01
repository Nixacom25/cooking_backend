'use strict';

const DEFAULT_DNA = Object.freeze({
    Spice: 50,
    Sweet: 50,
    Crunchy: 50,
});

/**
 * Skill level enum — maps named levels to percentages (0-100).
 * Used to allow both enum labels and percentages in user input.
 */
const SKILL_LEVEL_ENUM = Object.freeze({
    'total_beginer': 20,
    'HomeCook': 45,
    'ConfidentCook': 70,
    'Advanced Semi Pro': 90,
});

/**
 * Default skill level — intermediate cook (50%).
 */
const DEFAULT_SKILL_LEVEL = Object.freeze({
    label: null,
    percent: 50,
});

/**
 * Any-time sentinel. Stored as null in the normalised form.
 */
const DEFAULT_TIME_MINUTES = null;

/**
 * Maximum supported cook time for validation and parsing.
 */
const MAX_TIME_MINUTES = 24 * 60;

/**
 * Any-servings sentinel. Stored as null in the normalised form.
 */
const DEFAULT_SERVINGS = null;

/**
 * Maximum supported servings for validation and parsing.
 */
const MAX_SERVINGS = 50;

/**
 * Common cuisine typo/synonym mapping to canonical slugs.
 */
const CUISINE_ALIASES = Object.freeze({
    pakstani: 'pakistani',
    pakisthani: 'pakistani',
    pakistani: 'pakistani',
    italian: 'italian',
    italianfood: 'italian',
    chinese: 'chinese',
    chinise: 'chinese',
    mexican: 'mexican',
    mexcian: 'mexican',
    indian: 'indian',
    indan: 'indian',
    thai: 'thai',
    japanese: 'japanese',
    japanesefood: 'japanese',
    mediterranean: 'mediterranean',
    middleeastern: 'middle-eastern',
    mideastern: 'middle-eastern',
    middleeasternfood: 'middle-eastern',
    american: 'american',
    french: 'french',
    turkish: 'turkish',
    greek: 'greek',
    korean: 'korean',
    spanish: 'spanish',
});

/**
 * Common kitchen tool typo/synonym mapping to canonical slugs.
 */
const KITCHEN_TOOL_ALIASES = Object.freeze({
    oven: 'oven',
    gasburner: 'gas-burner',
    gasstove: 'gas-burner',
    gascooker: 'gas-burner',
    microwave: 'microwave',
    microwaveoven: 'microwave',
    airfryer: 'air-fryer',
    airfryar: 'air-fryer',
    blender: 'blender',
    foodprocessor: 'food-processor',
    foodproccessor: 'food-processor',
    instantpot: 'instant-pot',
    pressurecooker: 'instant-pot',
    grill: 'grill-bbq',
    bbq: 'grill-bbq',
    grillbbq: 'grill-bbq',
    ricecooker: 'rice-cooker',
    standmixer: 'stand-mixer',
    steamer: 'steamer',
    streamer: 'steamer',
});

/**
 * Common cooking goal typo/synonym mapping to canonical slugs.
 */
const COOKING_GOAL_ALIASES = Object.freeze({
    cookmoreathome: 'cook-more-at-home-save-money',
    savemoney: 'cook-more-at-home-save-money',
    cookmoreathomeandsavemoney: 'cook-more-at-home-save-money',
    reducefoodwaste: 'reduce-food-waste',
    reducefoodwasteusewhatihave: 'reduce-food-waste',
    'reduce-food-waste-use-what-i-have': 'reduce-food-waste',
    usewhatihave: 'reduce-food-waste',
    eathealthier: 'eat-healthier-track-nutrition',
    tracknutrition: 'eat-healthier-track-nutrition',
    eathealthierandtracknutrition: 'eat-healthier-track-nutrition',
    discovernewcuisines: 'discover-new-cuisines-recipes',
    discovernewrecipes: 'discover-new-cuisines-recipes',
    discovernewcuisinesandrecipes: 'discover-new-cuisines-recipes',
    mealprep: 'meal-prep-plan-week',
    planmyweek: 'meal-prep-plan-week',
    mealprepandplanmyweek: 'meal-prep-plan-week',
    learntocookfromscratch: 'learn-to-cook-from-scratch',
    learntocookfromscrath: 'learn-to-cook-from-scratch',
    learntocook: 'learn-to-cook-from-scratch',
});

/**
 * Normalises free-text labels to lowercase kebab-case.
 *
 * @param {unknown} value
 * @returns {string}
 */
function slugifyPreferenceLabel(value) {
    return String(value || '')
        .trim()
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/^-+|-+$/g, '');
}

/**
 * Returns a normalised DNA profile with required defaults merged in.
 * Additional DNA keys are preserved as long as they are finite numbers.
 *
 * @param {Record<string, number>} [input]
 * @returns {Record<string, number>}
 */
function normaliseDNA(input = {}) {
    const merged = { ...DEFAULT_DNA };

    if (!input || typeof input !== 'object' || Array.isArray(input)) {
        return merged;
    }

    for (const [key, rawValue] of Object.entries(input)) {
        const normalisedKey = String(key || '').trim();
        const value = Number(rawValue);

        if (!normalisedKey || !Number.isFinite(value)) continue;
        merged[normalisedKey] = value;
    }

    return merged;
}

/**
 * Normalises skill level input to canonical format { label, percent }.
 * Accepts:
 *  - Named level string (e.g., 'HomeCook', 'Advanced Semi Pro')
 *  - Direct percentage number (0-100)
 *  - Object { label?, percent } or { percent }
 *  - null/undefined (defaults to 50%)
 *
 * @param {string|number|Object|null} [input]
 * @returns {{ label: string|null, percent: number }}
 */
function normaliseSkillLevel(input = null) {
    // Case 1: Direct percentage as number
    if (typeof input === 'number') {
        if (input >= 0 && input <= 100) {
            return { label: null, percent: Math.round(input) };
        }
        return { ...DEFAULT_SKILL_LEVEL };
    }

    // Case 2: Named level as string
    if (typeof input === 'string') {
        const trimmed = String(input).trim();
        if (trimmed in SKILL_LEVEL_ENUM) {
            const percent = SKILL_LEVEL_ENUM[trimmed];
            return { label: trimmed, percent };
        }
        return { ...DEFAULT_SKILL_LEVEL };
    }

    // Case 3: Object with percent and optional label
    if (input && typeof input === 'object' && !Array.isArray(input)) {
        const percent = Number(input.percent);
        if (Number.isFinite(percent) && percent >= 0 && percent <= 100) {
            return {
                label: (typeof input.label === 'string' && input.label in SKILL_LEVEL_ENUM) ? input.label : null,
                percent: Math.round(percent),
            };
        }
        return { ...DEFAULT_SKILL_LEVEL };
    }

    // Case 4: null, undefined, or invalid — default
    return { ...DEFAULT_SKILL_LEVEL };
}

/**
 * Attempts to parse a time preference into a canonical representation.
 *
 * Supported input forms:
 * - null / undefined / -1 / "any" => null (any time)
 * - number => exact minutes
 * - { min, max } => range in minutes
 * - strings such as "30", "30-45 min", "1-2 hours", "1 hour"
 *
 * Canonical output:
 * - null for any time
 * - number for exact minutes
 * - { min, max } for a range
 *
 * @param {unknown} input
 * @returns {{ valid: boolean, value: null | number | { min: number, max: number } }}
 */
function parseTimePreference(input = null) {
    if (input == null) {
        return { valid: true, value: DEFAULT_TIME_MINUTES };
    }

    if (typeof input === 'number') {
        if (input === -1) {
            return { valid: true, value: DEFAULT_TIME_MINUTES };
        }

        if (!Number.isInteger(input) || input < 0 || input > MAX_TIME_MINUTES) {
            return { valid: false, value: DEFAULT_TIME_MINUTES };
        }

        return { valid: true, value: input };
    }

    if (typeof input === 'string') {
        const trimmed = input.trim().toLowerCase();

        if (!trimmed || 
            trimmed === 'any' || 
            trimmed === 'any time' || 
            trimmed === 'anytime' || 
            trimmed === 'any amount of time' || 
            trimmed === '-1') {
            return { valid: true, value: DEFAULT_TIME_MINUTES };
        }

        const hourRangeMatch = trimmed.match(/^(\d+(?:\.\d+)?)\s*(?:-|–|—|to)\s*(\d+(?:\.\d+)?)\s*(hours?|hrs?|h)\b/);
        if (hourRangeMatch) {
            const min = Math.round(Number(hourRangeMatch[1]) * 60);
            const max = Math.round(Number(hourRangeMatch[2]) * 60);
            if (!Number.isFinite(min) || !Number.isFinite(max) || min < 0 || max < min || max > MAX_TIME_MINUTES) {
                return { valid: false, value: DEFAULT_TIME_MINUTES };
            }
            return { valid: true, value: min === max ? min : { min, max } };
        }

        const minuteRangeMatch = trimmed.match(/^(\d+(?:\.\d+)?)\s*(?:-|–|—|to)\s*(\d+(?:\.\d+)?)\s*(minutes?|mins?|m)\b/);
        if (minuteRangeMatch) {
            const min = Math.round(Number(minuteRangeMatch[1]));
            const max = Math.round(Number(minuteRangeMatch[2]));
            if (!Number.isFinite(min) || !Number.isFinite(max) || min < 0 || max < min || max > MAX_TIME_MINUTES) {
                return { valid: false, value: DEFAULT_TIME_MINUTES };
            }
            return { valid: true, value: min === max ? min : { min, max } };
        }

        const underMatch = trimmed.match(/^(?:under|less than|up to)\s*(\d+(?:\.\d+)?)\s*(minutes?|mins?|m|hours?|hrs?|h)?\b/);
        if (underMatch) {
            let val = Number(underMatch[1]);
            const unit = underMatch[2] || 'm';
            if (unit.startsWith('h')) val *= 60;
            const max = Math.round(val);
            if (!Number.isFinite(max) || max < 0 || max > MAX_TIME_MINUTES) {
                return { valid: false, value: DEFAULT_TIME_MINUTES };
            }
            return { valid: true, value: { min: 0, max } };
        }

        const exactHourMatch = trimmed.match(/^(\d+(?:\.\d+)?)\s*(hours?|hrs?|h)\b/);
        if (exactHourMatch) {
            const minutes = Math.round(Number(exactHourMatch[1]) * 60);
            if (!Number.isFinite(minutes) || minutes < 0 || minutes > MAX_TIME_MINUTES) {
                return { valid: false, value: DEFAULT_TIME_MINUTES };
            }
            return { valid: true, value: minutes };
        }

        const exactMinuteMatch = trimmed.match(/^(\d+(?:\.\d+)?)\s*(minutes?|mins?|m)?$/);
        if (exactMinuteMatch) {
            const minutes = Math.round(Number(exactMinuteMatch[1]));
            if (!Number.isFinite(minutes) || minutes < 0 || minutes > MAX_TIME_MINUTES) {
                return { valid: false, value: DEFAULT_TIME_MINUTES };
            }
            return { valid: true, value: minutes };
        }

        return { valid: false, value: DEFAULT_TIME_MINUTES };
    }

    if (typeof input === 'object' && !Array.isArray(input)) {
        const minRaw = input.min ?? input.min_minutes ?? input.minimum;
        const maxRaw = input.max ?? input.max_minutes ?? input.maximum;

        if (minRaw == null && maxRaw == null) {
            if (input.value != null) {
                return parseTimePreference(input.value);
            }
            if (input.minutes != null) {
                return parseTimePreference(input.minutes);
            }
            return { valid: false, value: DEFAULT_TIME_MINUTES };
        }

        const min = Number(minRaw);
        const max = Number(maxRaw);
        if (!Number.isInteger(min) || !Number.isInteger(max) || min < 0 || max < min || max > MAX_TIME_MINUTES) {
            return { valid: false, value: DEFAULT_TIME_MINUTES };
        }

        return { valid: true, value: min === max ? min : { min, max } };
    }

    return { valid: false, value: DEFAULT_TIME_MINUTES };
}

/**
 * Normalises a time preference into a canonical representation.
 * Returns null for "any time", a number for exact minutes, or a range object.
 *
 * @param {unknown} input
 * @returns {null | number | { min: number, max: number }}
 */
function normaliseTimePreference(input = null) {
    return parseTimePreference(input).value;
}

/**
 * Attempts to parse a servings preference into canonical representation.
 *
 * Supported input forms:
 * - null / undefined / -1 / "it varies" => null
 * - number => { min: n, max: n }
 * - { min, max } where max may be null => range
 * - strings such as "1", "couple", "3-4", "5-6", "7+", "it varies"
 *
 * Canonical output:
 * - null for varies/any
 * - { min, max } where max may be null for open-ended ranges
 *
 * @param {unknown} input
 * @returns {{ valid: boolean, value: null | { min: number, max: number|null } }}
 */
function parseServingsPreference(input = null) {
    if (input == null) {
        return { valid: true, value: DEFAULT_SERVINGS };
    }

    if (typeof input === 'number') {
        if (input === -1) {
            return { valid: true, value: DEFAULT_SERVINGS };
        }
        if (!Number.isInteger(input) || input < 1 || input > MAX_SERVINGS) {
            return { valid: false, value: DEFAULT_SERVINGS };
        }
        return { valid: true, value: { min: input, max: input } };
    }

    if (typeof input === 'string') {
        const trimmed = input.trim().toLowerCase();

        if (!trimmed || trimmed === '-1' || trimmed === 'it varies' || trimmed === 'varies' || trimmed === 'i will adjust' || trimmed === 'i will adjust myself' || trimmed === 'any') {
            return { valid: true, value: DEFAULT_SERVINGS };
        }

        if (trimmed === 'only me' || trimmed === 'just me' || trimmed === 'me') {
            return { valid: true, value: { min: 1, max: 1 } };
        }

        if (trimmed === 'couple' || trimmed === 'two' || trimmed === 'two people') {
            return { valid: true, value: { min: 2, max: 2 } };
        }

        const plusMatch = trimmed.match(/^(\d+)\+(?:\s*(?:people|persons|guests|servings))?$/);
        if (plusMatch) {
            const min = Number(plusMatch[1]);
            if (!Number.isInteger(min) || min < 1 || min > MAX_SERVINGS) {
                return { valid: false, value: DEFAULT_SERVINGS };
            }
            return { valid: true, value: { min, max: null } };
        }

        const rangeMatch = trimmed.match(/^(\d+)\s*(?:-|–|—|to)\s*(\d+)(?:\s*(?:people|persons|guests|servings))?$/);
        if (rangeMatch) {
            const min = Number(rangeMatch[1]);
            const max = Number(rangeMatch[2]);
            if (!Number.isInteger(min) || !Number.isInteger(max) || min < 1 || max < min || max > MAX_SERVINGS) {
                return { valid: false, value: DEFAULT_SERVINGS };
            }
            return { valid: true, value: { min, max } };
        }

        const singleMatch = trimmed.match(/^(\d+)$/);
        if (singleMatch) {
            const n = Number(singleMatch[1]);
            if (!Number.isInteger(n) || n < 1 || n > MAX_SERVINGS) {
                return { valid: false, value: DEFAULT_SERVINGS };
            }
            return { valid: true, value: { min: n, max: n } };
        }

        return { valid: false, value: DEFAULT_SERVINGS };
    }

    if (typeof input === 'object' && !Array.isArray(input)) {
        const minRaw = input.min ?? input.minimum;
        const maxRaw = input.max ?? input.maximum;

        if (minRaw == null && maxRaw == null) {
            if (input.value != null) {
                return parseServingsPreference(input.value);
            }
            return { valid: false, value: DEFAULT_SERVINGS };
        }

        const min = Number(minRaw);
        const max = maxRaw == null ? null : Number(maxRaw);

        if (!Number.isInteger(min) || min < 1 || min > MAX_SERVINGS) {
            return { valid: false, value: DEFAULT_SERVINGS };
        }

        if (max !== null) {
            if (!Number.isInteger(max) || max < min || max > MAX_SERVINGS) {
                return { valid: false, value: DEFAULT_SERVINGS };
            }
        }

        return { valid: true, value: { min, max } };
    }

    return { valid: false, value: DEFAULT_SERVINGS };
}

/**
 * Normalises servings preference into canonical representation.
 * Returns null for varies/any, or { min, max } (max can be null).
 *
 * @param {unknown} input
 * @returns {null | { min: number, max: number|null }}
 */
function normaliseServingsPreference(input = null) {
    return parseServingsPreference(input).value;
}

/**
 * Normalises cuisines_love input into an array of canonical names.
 * Accepts arrays and comma-separated strings.
 * Unknown cuisines are preserved as slugified values for future extensibility.
 *
 * @param {unknown} input
 * @returns {string[]}
 */
function normaliseCuisinesLove(input = []) {
    if (input == null) return [];

    const list = Array.isArray(input)
        ? input
        : (typeof input === 'string' ? input.split(',') : []);

    const out = [];
    const seen = new Set();

    for (const raw of list) {
        const slug = slugifyPreferenceLabel(raw);
        if (!slug) continue;

        const compact = slug.replace(/-/g, '');
        const canonical = CUISINE_ALIASES[compact] || CUISINE_ALIASES[slug] || slug;

        if (!seen.has(canonical)) {
            seen.add(canonical);
            out.push(canonical);
        }
    }

    return out;
}

/**
 * Normalises kitchen_tools input into an array of canonical tool names.
 * Accepts arrays and comma-separated strings.
 * Unknown tools are preserved as slugified values for future extensibility.
 *
 * @param {unknown} input
 * @returns {string[]}
 */
function normaliseKitchenTools(input = []) {
    if (input == null) return [];

    const list = Array.isArray(input)
        ? input
        : (typeof input === 'string' ? input.split(',') : []);

    const out = [];
    const seen = new Set();

    for (const raw of list) {
        const slug = slugifyPreferenceLabel(raw);
        if (!slug) continue;

        const compact = slug.replace(/-/g, '');
        const canonical = KITCHEN_TOOL_ALIASES[compact] || KITCHEN_TOOL_ALIASES[slug] || slug;

        if (!seen.has(canonical)) {
            seen.add(canonical);
            out.push(canonical);
        }
    }

    return out;
}

/**
 * Normalises cooking_goals input into an array of canonical goal names.
 * Accepts arrays and comma-separated strings.
 * Unknown goals are preserved as slugified values for future extensibility.
 *
 * @param {unknown} input
 * @returns {string[]}
 */
function normaliseCookingGoals(input = []) {
    if (input == null) return [];

    const list = Array.isArray(input)
        ? input
        : (typeof input === 'string' ? input.split(',') : []);

    const out = [];
    const seen = new Set();

    for (const raw of list) {
        const slug = slugifyPreferenceLabel(raw);
        if (!slug) continue;

        const compact = slug.replace(/-/g, '');
        const canonical = COOKING_GOAL_ALIASES[compact] || COOKING_GOAL_ALIASES[slug] || slug;

        if (!seen.has(canonical)) {
            seen.add(canonical);
            out.push(canonical);
        }
    }

    return out;
}

module.exports = {
    DEFAULT_DNA,
    SKILL_LEVEL_ENUM,
    DEFAULT_SKILL_LEVEL,
    DEFAULT_TIME_MINUTES,
    DEFAULT_SERVINGS,
    normaliseDNA,
    normaliseSkillLevel,
    MAX_TIME_MINUTES,
    MAX_SERVINGS,
    parseTimePreference,
    normaliseTimePreference,
    parseServingsPreference,
    normaliseServingsPreference,
    normaliseCuisinesLove,
    normaliseKitchenTools,
    normaliseCookingGoals,
};
