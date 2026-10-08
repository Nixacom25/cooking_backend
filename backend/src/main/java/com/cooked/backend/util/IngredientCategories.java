package com.cooked.backend.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Best-guess library category of an ingredient from its normalized key (see {@link IngredientKeys}).
 * Rules are checked in order, so "black_pepper" is a spice before "pepper" is a pepper; unknown → "Other".
 */
public final class IngredientCategories {

    public static final String OTHER = "Other";

    private static final Map<String, List<String>> RULES = new LinkedHashMap<>();

    static {
        RULES.put("Common Spices", List.of("black_pepper", "white_pepper", "ground_pepper", "peppercorn", "pepper_flake", "red_pepper_flake",
                "cayenne", "paprika", "cumin", "turmeric", "cinnamon", "nutmeg", "clove", "allspice", "cardamom", "coriander_seed",
                "curry", "garam_masala", "chili_powder", "chilli_powder", "spice", "seasoning", "salt", "bay_leaf", "oregano",
                "saffron", "star_anise", "fenugreek", "sumac", "za_atar", "ginger_powder", "ground_ginger", "garlic_powder",
                "onion_powder", "vanilla", "netetou", "soumbala", "dawadawa"));
        RULES.put("Soups & Stocks", List.of("broth", "stock", "bouillon", "maggi", "soup"));
        RULES.put("Sauces & Condiments", List.of("sauce", "ketchup", "mustard", "mayonnaise", "mayo", "vinegar", "soy", "salsa", "pesto",
                "dressing", "tahini", "harissa", "sriracha", "relish", "tomato_paste", "paste"));
        RULES.put("Oils", List.of("oil", "ghee", "shortening", "lard"));
        RULES.put("Nuts & Seeds", List.of("nut", "almond", "peanut", "cashew", "walnut", "pecan", "pistachio", "hazelnut", "sesame",
                "seed", "chia", "flax", "pine_nut"));
        RULES.put("Cheese", List.of("cheese", "cheddar", "mozzarella", "parmesan", "feta", "ricotta", "gouda", "brie", "halloumi",
                "mascarpone", "pecorino", "gruyere", "camembert"));
        RULES.put("Dairy", List.of("milk", "cream", "butter", "yogurt", "yoghurt", "buttermilk", "kefir", "creme_fraiche"));
        RULES.put("Eggs", List.of("egg", "yolk"));
        RULES.put("Poultry", List.of("chicken", "turkey", "duck", "quail", "poultry"));
        RULES.put("Fish & Seafood", List.of("fish", "salmon", "tuna", "shrimp", "prawn", "cod", "crab", "lobster", "mussel", "clam",
                "squid", "octopus", "anchovy", "sardine", "tilapia", "mackerel", "trout", "scallop", "oyster", "haddock", "seafood", "thiof"));
        RULES.put("Red meat", List.of("beef", "pork", "lamb", "bacon", "sausage", "ham", "veal", "goat", "mutton", "steak", "mince",
                "ground_meat", "chorizo", "salami", "pepperoni", "prosciutto", "meat", "rib", "brisket"));
        RULES.put("Pasta & Noodles", List.of("pasta", "spaghetti", "noodle", "macaroni", "penne", "lasagna", "lasagne", "fettuccine",
                "linguine", "ramen", "udon", "vermicelli", "fusilli", "rigatoni", "orzo", "gnocchi"));
        RULES.put("Bread & Bakery", List.of("bread", "bun", "tortilla", "pita", "baguette", "naan", "croissant", "bagel", "brioche", "roll", "wrap"));
        RULES.put("Baking", List.of("flour", "baking_powder", "baking_soda", "yeast", "cornstarch", "starch", "cocoa", "chocolate", "gelatin"));
        RULES.put("Sweeteners", List.of("sugar", "honey", "syrup", "molasses", "stevia", "agave", "sweetener"));
        RULES.put("Grains & Rice", List.of("rice", "quinoa", "oat", "couscous", "bulgur", "barley", "millet", "cornmeal", "polenta",
                "semolina", "grain", "fonio", "sorghum", "attieke"));
        RULES.put("Beans & Legumes", List.of("bean", "lentil", "chickpea", "pea", "edamame", "tofu", "tempeh", "black_eyed"));
        RULES.put("Fresh Herbs", List.of("basil", "parsley", "cilantro", "coriander", "mint", "thyme", "rosemary", "dill", "sage",
                "chive", "tarragon", "herb", "lemongrass"));
        RULES.put("Alliums", List.of("onion", "garlic", "shallot", "leek", "scallion"));
        RULES.put("Tomatoes", List.of("tomato"));
        RULES.put("Peppers", List.of("pepper", "chili", "chilli", "jalapeno", "habanero", "capsicum", "poblano", "chipotle"));
        RULES.put("Citrus", List.of("lemon", "lime", "orange", "grapefruit", "clementine", "tangerine", "citrus", "yuzu"));
        RULES.put("Berries", List.of("berry", "strawberry", "raspberry", "blueberry", "blackberry", "cranberry"));
        RULES.put("Tropical fruit", List.of("mango", "pineapple", "banana", "papaya", "coconut", "plantain", "passion_fruit", "guava",
                "avocado", "baobab", "bissap", "hibiscus"));
        RULES.put("Fruit", List.of("apple", "pear", "peach", "grape", "cherry", "plum", "apricot", "fig", "date", "raisin", "melon",
                "watermelon", "kiwi", "pomegranate", "nectarine", "fruit"));
        RULES.put("Leafy greens", List.of("lettuce", "spinach", "kale", "cabbage", "arugula", "rocket", "chard", "collard", "greens",
                "watercress", "bok_choy", "endive"));
        RULES.put("Potatoes & starchy", List.of("potato", "yam", "cassava", "taro", "manioc", "ube"));
        RULES.put("Root vegetables", List.of("carrot", "beet", "beetroot", "radish", "turnip", "parsnip", "ginger", "celeriac"));
        RULES.put("Vegetables", List.of("zucchini", "courgette", "eggplant", "aubergine", "cucumber", "broccoli", "cauliflower", "mushroom",
                "celery", "asparagus", "okra", "squash", "pumpkin", "corn", "artichoke", "brussels", "fennel", "olive", "vegetable"));
        RULES.put("Drinks", List.of("water", "juice", "wine", "beer", "coffee", "tea", "soda", "rum", "vodka", "whisky", "brandy", "sake", "liqueur"));
    }

    private IngredientCategories() {
    }

    public static String guess(String key) {
        if (key == null || key.isBlank()) return OTHER;
        Set<String> tokens = Set.of(key.split("_"));
        String padded = "_" + key + "_";
        for (Map.Entry<String, List<String>> rule : RULES.entrySet()) {
            for (String word : rule.getValue()) {
                boolean hit = word.contains("_") ? padded.contains("_" + word + "_") || padded.contains("_" + word) : tokens.contains(word);
                if (hit) return rule.getKey();
            }
        }
        return OTHER;
    }
}
