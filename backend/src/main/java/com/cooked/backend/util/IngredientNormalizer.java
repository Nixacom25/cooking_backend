package com.cooked.backend.util;

import com.cooked.backend.dto.request.InstacartIngredientDto;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IngredientNormalizer {

    private static final Set<String> KNOWN_UNITS = new HashSet<>(Arrays.asList(
            "piece", "pieces", "g", "kg", "gram", "grams", "kilogram", "kilograms",
            "ml", "l", "liter", "liters", "oz", "ounce", "ounces", "lb", "lbs", "pound", "pounds",
            "cup", "cups", "tbsp", "tablespoon", "tablespoons", "tsp", "teaspoon", "teaspoons",
            "clove", "cloves", "slice", "slices", "bottle", "bottles", "can", "cans",
            "handful", "handfuls", "fillet", "fillets", "steak", "steaks", "head", "heads", "pinch", "pinches"
    ));

    private static final List<String> PREPARATION_WORDS = Arrays.asList(
            "chopped", "diced", "minced", "sliced", "peeled", "cooked", "raw", "fresh",
            "frozen", "organic", "to taste", "large", "small", "medium", "finely",
            "coarsely", "grated", "shredded", "mashed", "crushed", "optional"
    );

    private static final Pattern LEADING_NUMBER_PATTERN = Pattern.compile("^([0-9]+(?:[.,][0-9]+)?(?:/[0-9]+)?)\\s*");

    public static InstacartIngredientDto normalize(String rawName, String rawQuantity) {
        if (rawName == null || rawName.trim().isEmpty()) {
            return null;
        }

        String nameInput = rawName.trim();
        String qtyInput = rawQuantity != null ? rawQuantity.trim() : "";

        Double quantity = null;
        String unit = null;
        List<String> notesList = new ArrayList<>();

        // 1. Try parsing quantity from rawQuantity first
        if (!qtyInput.isEmpty()) {
            Matcher qtyMatcher = LEADING_NUMBER_PATTERN.matcher(qtyInput);
            if (qtyMatcher.find()) {
                quantity = parseDouble(qtyMatcher.group(1));
                String remainder = qtyInput.substring(qtyMatcher.end()).trim().toLowerCase();
                if (!remainder.isEmpty()) {
                    unit = extractUnit(remainder);
                    if (unit == null && !remainder.isEmpty()) {
                        notesList.add(remainder);
                    }
                }
            } else {
                unit = extractUnit(qtyInput.toLowerCase());
                if (unit == null) {
                    notesList.add(qtyInput);
                }
            }
        }

        // 2. If quantity wasn't in rawQuantity, check if nameInput starts with a number
        String workingName = nameInput;
        if (quantity == null) {
            Matcher nameNumMatcher = LEADING_NUMBER_PATTERN.matcher(workingName);
            if (nameNumMatcher.find()) {
                quantity = parseDouble(nameNumMatcher.group(1));
                workingName = workingName.substring(nameNumMatcher.end()).trim();
            }
        }

        // 3. Extract unit from workingName if unit is still null
        if (unit == null && !workingName.isEmpty()) {
            String[] tokens = workingName.split("\\s+");
            if (tokens.length > 0) {
                String firstWord = tokens[0].toLowerCase().replaceAll("[^a-z]", "");
                if (KNOWN_UNITS.contains(firstWord)) {
                    unit = standardizeUnit(firstWord);
                    workingName = workingName.substring(tokens[0].length()).trim();
                }
            }
        }

        // Default unit for pure numeric quantities
        if (quantity != null && unit == null) {
            unit = "pieces";
        }

        // 4. Extract preparation/notes words from workingName
        workingName = workingName.replaceAll("[,()]", " ");
        String[] words = workingName.split("\\s+");
        List<String> cleanNameTokens = new ArrayList<>();

        for (String word : words) {
            String cleanWord = word.trim();
            if (cleanWord.isEmpty()) continue;

            String lowerWord = cleanWord.toLowerCase();
            if (PREPARATION_WORDS.contains(lowerWord)) {
                notesList.add(lowerWord);
            } else {
                cleanNameTokens.add(cleanWord);
            }
        }

        String finalName = String.join(" ", cleanNameTokens).trim();
        if (finalName.isEmpty()) {
            finalName = nameInput.replaceAll("^[0-9\\s,.]+", "").trim();
            if (finalName.isEmpty()) {
                finalName = nameInput;
            }
        }

        String notesStr = notesList.isEmpty() ? null : String.join(", ", notesList);

        return InstacartIngredientDto.builder()
                .name(finalName.toLowerCase())
                .quantity(quantity)
                .unit(unit)
                .notes(notesStr)
                .build();
    }

    private static Double parseDouble(String str) {
        try {
            if (str.contains("/")) {
                String[] parts = str.split("/");
                double num = Double.parseDouble(parts[0].trim());
                double den = Double.parseDouble(parts[1].trim());
                return den != 0 ? num / den : null;
            }
            return Double.parseDouble(str.replace(',', '.'));
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractUnit(String text) {
        String[] tokens = text.split("\\s+");
        for (String token : tokens) {
            String clean = token.replaceAll("[^a-z]", "");
            if (KNOWN_UNITS.contains(clean)) {
                return standardizeUnit(clean);
            }
        }
        return null;
    }

    private static String standardizeUnit(String unit) {
        switch (unit.toLowerCase()) {
            case "g":
            case "gram":
            case "grams":
                return "g";
            case "kg":
            case "kilogram":
            case "kilograms":
                return "kg";
            case "ml":
                return "ml";
            case "l":
            case "liter":
            case "liters":
                return "l";
            case "oz":
            case "ounce":
            case "ounces":
                return "oz";
            case "lb":
            case "lbs":
            case "pound":
            case "pounds":
                return "lbs";
            case "tbsp":
            case "tablespoon":
            case "tablespoons":
                return "tablespoons";
            case "tsp":
            case "teaspoon":
            case "teaspoons":
                return "teaspoons";
            case "cup":
            case "cups":
                return "cups";
            case "clove":
            case "cloves":
                return "cloves";
            case "slice":
            case "slices":
                return "slices";
            case "bottle":
            case "bottles":
                return "bottles";
            case "can":
            case "cans":
                return "cans";
            default:
                return unit.toLowerCase();
        }
    }
}
