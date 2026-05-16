package com.cooked.backend.service.impl;

import com.cooked.backend.entity.CategoryType;
import com.cooked.backend.entity.RecipeCategory;
import com.cooked.backend.repository.RecipeCategoryRepository;
import com.cooked.backend.service.TaxonomyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaxonomyServiceImpl implements TaxonomyService {

    private final RecipeCategoryRepository recipeCategoryRepository;
    private final JdbcTemplate jdbcTemplate;

    private static final Map<String, String> CATEGORY_IMAGES = Map.ofEntries(
        Map.entry("High Protein Picks", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825794/ai-recipe-app/taxonomy/gjndqlconjidynpzgljm.png"),
        Map.entry("Easy Desserts", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825799/ai-recipe-app/taxonomy/t8smxtmx8fo5dgu6nq09.png"),
        Map.entry("30-Minute Meals", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825804/ai-recipe-app/taxonomy/nllnev1tt8sbyhzblxnt.png"),
        Map.entry("Healthy Breakfasts", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825810/ai-recipe-app/taxonomy/chip5aztermtfu1e1rn7.jpg"),
        Map.entry("Plant-Based Essentials", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825816/ai-recipe-app/taxonomy/uyjylmhr9lv0m6kznfnv.png"),
        Map.entry("Low-Carb Meals", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825822/ai-recipe-app/taxonomy/rcnlugjoz4wgha3wbip1.png"),
        Map.entry("Bowls & Salads", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825828/ai-recipe-app/taxonomy/pfftssqpcneggog8skvr.jpg"),
        Map.entry("Desserts", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825832/ai-recipe-app/taxonomy/jsf51txzntu3ouv8lecr.jpg"),
        Map.entry("Pasta & Noodles", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825833/ai-recipe-app/taxonomy/zl9pii59boskpsazbsf9.jpg"),
        Map.entry("Pizza & Flatbreads", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825835/ai-recipe-app/taxonomy/d9g359mpsmvqvdkbah7u.jpg"),
        Map.entry("Curries & Stews", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825839/ai-recipe-app/taxonomy/zkaveyb5ror0ogsxa2a3.jpg"),
        Map.entry("Handheld / Street Food", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825849/ai-recipe-app/taxonomy/dbnakseazqptrnppmxh4.jpg"),
        Map.entry("Soups", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825857/ai-recipe-app/taxonomy/lxx4ebaywhubtnt56s7f.jpg"),
        Map.entry("Rice & Grain", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825868/ai-recipe-app/taxonomy/cnbaetrmnteyizx4wt9g.jpg"),
        Map.entry("Stir Fry", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825877/ai-recipe-app/taxonomy/uxszdotoyanj9raincra.jpg")
    );

    private static final Map<String, String> CUISINE_IMAGES = Map.ofEntries(
        Map.entry("Italian", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825879/ai-recipe-app/taxonomy/fgke47uvihcznhh26xyw.png"),
        Map.entry("Mexican", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825881/ai-recipe-app/taxonomy/v8ompn1xfg2xerin57ea.png"),
        Map.entry("Chinese", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825883/ai-recipe-app/taxonomy/t0dzizwahawi5lrdqnrx.png"),
        Map.entry("Japanese", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825885/ai-recipe-app/taxonomy/rkaaoetvlogvojyrbbal.png"),
        Map.entry("Thai", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825887/ai-recipe-app/taxonomy/kpf7vnohqamnafqlj9xn.png"),
        Map.entry("Indian", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825896/ai-recipe-app/taxonomy/fhzwu5howhuiui6mdxl9.jpg"),
        Map.entry("Korean", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825899/ai-recipe-app/taxonomy/az5qhmzv8ktvmybrgodx.jpg"),
        Map.entry("Mediterranean", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825909/ai-recipe-app/taxonomy/tv6ffhbuwenbqjycdf2m.png"),
        Map.entry("Middle Eastern", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825913/ai-recipe-app/taxonomy/kjclia9clnpalj5hmrr8.png"),
        Map.entry("French", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825928/ai-recipe-app/taxonomy/sqhgsctwb0ywicd4sivq.png"),
        Map.entry("Greek", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825941/ai-recipe-app/taxonomy/gtbhwfmco1mqo7j5n10q.png"),
        Map.entry("Caribbean", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825954/ai-recipe-app/taxonomy/mvkpcxrsmpt7rtjxkzrw.png"),
        Map.entry("West African", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825961/ai-recipe-app/taxonomy/pvqffpmrz1l2bjuoaabh.png"),
        Map.entry("American", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825964/ai-recipe-app/taxonomy/edm1eoulgyyj6ajo9sfx.jpg"),
        Map.entry("Senegalese", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825968/ai-recipe-app/taxonomy/qtghqgyehufyrqvohxcy.jpg"),
        Map.entry("Vietnamese", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825974/ai-recipe-app/taxonomy/ea3iepmpd92dykzekamg.jpg"),
        Map.entry("Moroccan", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825987/ai-recipe-app/taxonomy/cfn9mjfjtxbz6c0l4vsi.jpg"),
        Map.entry("Asian", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825996/ai-recipe-app/taxonomy/fsfudpdnqvfqyqtwpwhj.jpg"),
        Map.entry("Brazilian", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778826007/ai-recipe-app/taxonomy/rfdrinxhadmq6lsaj6uw.jpg"),
        Map.entry("British", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778826016/ai-recipe-app/taxonomy/mr6jkzdtemoulb4p436p.jpg")
    );

    @Override
    @Transactional
    public RecipeCategory getOrCreateCategory(String name, CategoryType type) {
        if (name == null || name.isBlank()) return null;
        
        String normalizedName = name.trim();
        if (type == CategoryType.CATEGORY) {
            if (normalizedName.equalsIgnoreCase("Breakfast")) normalizedName = "Healthy Breakfasts";
            if (normalizedName.equalsIgnoreCase("Handheld Street Food")) normalizedName = "Handheld / Street Food";
            if (normalizedName.equalsIgnoreCase("Rice & Grain Dishes")) normalizedName = "Rice & Grain";
            if (normalizedName.equalsIgnoreCase("Stir Fry Sauté Wok")) normalizedName = "Stir Fry";
            if (normalizedName.equalsIgnoreCase("High Protein")) normalizedName = "High Protein Picks";
            if (normalizedName.equalsIgnoreCase("Protein Plates")) normalizedName = "High Protein Picks";
            if (normalizedName.equalsIgnoreCase("Sides & Snacks")) normalizedName = "Handheld / Street Food";
            if (normalizedName.equalsIgnoreCase("Lunch")) normalizedName = "30-Minute Meals";
            if (normalizedName.equalsIgnoreCase("Quick Lunches")) normalizedName = "30-Minute Meals";
            if (normalizedName.equalsIgnoreCase("Vegan Essentials")) normalizedName = "Plant-Based Essentials";
            if (normalizedName.equalsIgnoreCase("Comfort Food")) normalizedName = "Pizza & Flatbreads";
            if (normalizedName.equalsIgnoreCase("Meal Prep Favorites")) normalizedName = "Healthy Breakfasts";
        } else if (type == CategoryType.CUISINE) {
            if (normalizedName.equalsIgnoreCase("Italy")) normalizedName = "Italian";
            if (normalizedName.equalsIgnoreCase("Mexico")) normalizedName = "Mexican";
            if (normalizedName.equalsIgnoreCase("Japan")) normalizedName = "Japanese";
            if (normalizedName.equalsIgnoreCase("France")) normalizedName = "French";
            if (normalizedName.equalsIgnoreCase("Thailand")) normalizedName = "Thai";
            if (normalizedName.equalsIgnoreCase("China")) normalizedName = "Chinese";
            if (normalizedName.equalsIgnoreCase("India")) normalizedName = "Indian";
            if (normalizedName.equalsIgnoreCase("South Korea")) normalizedName = "Korean";
            if (normalizedName.equalsIgnoreCase("Middle East")) normalizedName = "Middle Eastern";
            if (normalizedName.equalsIgnoreCase("West Africa")) normalizedName = "West African";
            if (normalizedName.equalsIgnoreCase("North American")) normalizedName = "American";
            if (normalizedName.equalsIgnoreCase("Japan")) normalizedName = "Japanese";
            if (normalizedName.equalsIgnoreCase("Japan Fusion")) normalizedName = "Japanese";
            if (normalizedName.equalsIgnoreCase("Thai/Chinese")) normalizedName = "Thai";
            if (normalizedName.equalsIgnoreCase("South East Asian")) normalizedName = "Thai";
            if (normalizedName.equalsIgnoreCase("Taiwanese")) normalizedName = "Chinese";
            if (normalizedName.equalsIgnoreCase("Argentine")) normalizedName = "Brazilian";
            if (normalizedName.equalsIgnoreCase("Belgian")) normalizedName = "French";
            if (normalizedName.equalsIgnoreCase("European")) normalizedName = "French";
            if (normalizedName.equalsIgnoreCase("Hawaiian")) normalizedName = "American";
            if (normalizedName.equalsIgnoreCase("Greece")) normalizedName = "Greek";
            if (normalizedName.equalsIgnoreCase("Spain")) normalizedName = "Spanish";
            if (normalizedName.equalsIgnoreCase("Brazil")) normalizedName = "Brazilian";
            if (normalizedName.equalsIgnoreCase("Turkey")) normalizedName = "Turkish";
            if (normalizedName.equalsIgnoreCase("Lebanon")) normalizedName = "Lebanese";
            if (normalizedName.equalsIgnoreCase("Vietnam")) normalizedName = "Vietnamese";
            if (normalizedName.equalsIgnoreCase("Morocco")) normalizedName = "Moroccan";
            if (normalizedName.equalsIgnoreCase("Japanese Fusion")) normalizedName = "Japanese";
            if (normalizedName.equalsIgnoreCase("Wast African")) normalizedName = "West African";
        }
        
        final String finalName = normalizedName;
        String mappedImage = (type == CategoryType.CUISINE) ? CUISINE_IMAGES.get(finalName) : CATEGORY_IMAGES.get(finalName);
        
        return recipeCategoryRepository.findByNameAndType(finalName, type)
                .map(existing -> {
                    // Update image if it's missing or different from our curated map
                    if (mappedImage != null && (existing.getImage() == null || !existing.getImage().equals(mappedImage))) {
                        existing.setImage(mappedImage);
                        return recipeCategoryRepository.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> recipeCategoryRepository.save(RecipeCategory.builder()
                        .name(finalName)
                        .type(type)
                        .image(mappedImage)
                        .build()));
    }

    @Override
    @Transactional
    public void migrateExistingRecipes() {
        log.info("Checking for legacy taxonomy columns in recipes table...");
        
        try {
            // Check if 'category' column exists
            Boolean hasCategory = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='recipes' AND column_name='category')", 
                Boolean.class
            );

            // Check if 'cuisine' column exists
            Boolean hasCuisine = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='recipes' AND column_name='cuisine')", 
                Boolean.class
            );

            if (Boolean.TRUE.equals(hasCategory)) {
                log.info("Migrating legacy 'category' column...");
                jdbcTemplate.query("SELECT id, category FROM recipes WHERE category_id IS NULL AND category IS NOT NULL", (rs, rowNum) -> {
                    UUID id = (UUID) rs.getObject("id");
                    String oldCat = rs.getString("category");
                    if (oldCat != null && !oldCat.isBlank()) {
                        RecipeCategory entity = getOrCreateCategory(oldCat, CategoryType.CATEGORY);
                        if (entity != null) {
                            jdbcTemplate.update("UPDATE recipes SET category_id = ? WHERE id = ?", entity.getId(), id);
                        }
                    }
                    return null;
                });
            }

            if (Boolean.TRUE.equals(hasCuisine)) {
                log.info("Migrating legacy 'cuisine' column...");
                jdbcTemplate.query("SELECT id, cuisine FROM recipes WHERE cuisine_id IS NULL AND cuisine IS NOT NULL", (rs, rowNum) -> {
                    UUID id = (UUID) rs.getObject("id");
                    String oldCui = rs.getString("cuisine");
                    if (oldCui != null && !oldCui.isBlank()) {
                        RecipeCategory entity = getOrCreateCategory(oldCui, CategoryType.CUISINE);
                        if (entity != null) {
                            jdbcTemplate.update("UPDATE recipes SET cuisine_id = ? WHERE id = ?", entity.getId(), id);
                        }
                    }
                    return null;
                });
            }

            if (Boolean.TRUE.equals(hasCategory) || Boolean.TRUE.equals(hasCuisine)) {
                log.info("Taxonomy migration completed. Cleaning up columns...");
                try {
                    if (Boolean.TRUE.equals(hasCategory)) jdbcTemplate.execute("ALTER TABLE recipes DROP COLUMN IF EXISTS category");
                    if (Boolean.TRUE.equals(hasCuisine)) jdbcTemplate.execute("ALTER TABLE recipes DROP COLUMN IF EXISTS cuisine");
                    log.info("Successfully dropped old taxonomy columns.");
                } catch (Exception e) {
                    log.warn("Could not drop old columns: {}", e.getMessage());
                }
            } else {
                log.info("No legacy taxonomy columns found. Skipping migration.");
            }

        } catch (Exception e) {
            log.error("Migration check/process failed: {}", e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void mergeDuplicateTaxonomies() {
        log.info("Starting taxonomy cleanup and merging...");
        
        Map<String, String> merges = Map.ofEntries(
            Map.entry("China", "Chinese"),
            Map.entry("Thailand", "Thai"),
            Map.entry("France", "French"),
            Map.entry("Italy", "Italian"),
            Map.entry("Mexico", "Mexican"),
            Map.entry("Japan", "Japanese"),
            Map.entry("India", "Indian"),
            Map.entry("South Korea", "Korean"),
            Map.entry("Middle East", "Middle Eastern"),
            Map.entry("Greece", "Greek"),
            Map.entry("Brazil", "Brazilian"),
            Map.entry("Vietnam", "Vietnamese"),
            Map.entry("Morocco", "Moroccan"),
            Map.entry("Spain", "Spanish"),
            Map.entry("Senegal", "Senegalese"),
            Map.entry("Sénégal", "Senegalese"),
            Map.entry("USA", "American"),
            Map.entry("America", "American"),
            Map.entry("United Kingdom", "British"),
            Map.entry("UK", "British"),
            Map.entry("Lebanon", "Lebanese"),
            Map.entry("Turkey", "Turkish"),
            Map.entry("West Africa", "West African"),
            Map.entry("Wast African", "West African"),
            Map.entry("Japanese Fusion", "Japanese"),
            Map.entry("Japan Fusion", "Japanese")
        );

        for (Map.Entry<String, String> entry : merges.entrySet()) {
            String oldName = entry.getKey();
            String newName = entry.getValue();

            // Find existing categories
            Map<String, UUID> map = jdbcTemplate.query("SELECT id, name FROM recipe_categories WHERE type = 'CUISINE' AND (name = ? OR name = ?)", (rs, rowNum) -> {
                UUID id = (UUID) rs.getObject("id");
                String name = rs.getString("name");
                return Map.entry(name, id);
            }, oldName, newName).stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));

            if (!map.isEmpty()) {
                UUID oldId = map.get(oldName);
                UUID newId = map.get(newName);

                if (oldId != null && newId != null) {
                    log.info("Merging '{}' into '{}'...", oldName, newName);
                    // Update recipes
                    jdbcTemplate.update("UPDATE recipes SET cuisine_id = ? WHERE cuisine_id = ?", newId, oldId);
                    // Delete old category
                    jdbcTemplate.update("DELETE FROM recipe_categories WHERE id = ?", oldId);
                } else if (oldId != null) {
                    log.info("Renaming '{}' to '{}'...", oldName, newName);
                    jdbcTemplate.update("UPDATE recipe_categories SET name = ? WHERE id = ?", newName, oldId);
                }
            }
        }
        log.info("Taxonomy cleanup completed.");
    }

    @Override
    public Map<String, String> getCategoryImages() { return CATEGORY_IMAGES; }

    @Override
    public Map<String, String> getCuisineImages() { return CUISINE_IMAGES; }
}
