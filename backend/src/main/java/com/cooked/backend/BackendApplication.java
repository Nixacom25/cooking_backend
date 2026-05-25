package com.cooked.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;

import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableScheduling
@EnableCaching
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

	@Bean
	CommandLineRunner commandLineRunner(JdbcTemplate jdbcTemplate) {
		return args -> {
			try {
				String[] tablesToFix = {
					"ALTER TABLE recipes ALTER COLUMN name TYPE TEXT",
					"ALTER TABLE recipes ALTER COLUMN category TYPE TEXT",
					"ALTER TABLE recipes ALTER COLUMN cuisine TYPE TEXT",
					"ALTER TABLE recipes ALTER COLUMN tips TYPE TEXT",
					"ALTER TABLE recipes ALTER COLUMN image TYPE TEXT",
					"ALTER TABLE recipes ALTER COLUMN source_url TYPE TEXT",
					
					"ALTER TABLE recipe_data ALTER COLUMN name TYPE TEXT",
					"ALTER TABLE recipe_data ALTER COLUMN image_url TYPE TEXT",
					
					"ALTER TABLE users ALTER COLUMN phone TYPE TEXT",
					"ALTER TABLE users ALTER COLUMN photo TYPE TEXT",
					"ALTER TABLE users ALTER COLUMN discovery_source TYPE TEXT",
					"ALTER TABLE users ALTER COLUMN other_discovery_source TYPE TEXT",
					"ALTER TABLE users ALTER COLUMN language TYPE TEXT",
					"ALTER TABLE users ALTER COLUMN country TYPE TEXT",
					"ALTER TABLE users ALTER COLUMN alternative_region TYPE TEXT",
					"ALTER TABLE users ALTER COLUMN measurement_system TYPE TEXT",
					"ALTER TABLE users ALTER COLUMN spice_level TYPE TEXT",
					"ALTER TABLE users ALTER COLUMN cooking_skill TYPE TEXT",
					"ALTER TABLE users ALTER COLUMN cooking_time_preference TYPE TEXT",
					"ALTER TABLE users ALTER COLUMN cooking_frequency TYPE TEXT",
					"ALTER TABLE users ALTER COLUMN cooking_target TYPE TEXT",
					"ALTER TABLE users ALTER COLUMN meal_planning_style TYPE TEXT",
					"ALTER TABLE users ALTER COLUMN original_transaction_id TYPE TEXT",
					
					"ALTER TABLE cookbooks ALTER COLUMN name TYPE TEXT",
					"ALTER TABLE grocery_items ALTER COLUMN quantity TYPE TEXT",
					"ALTER TABLE activities ALTER COLUMN title TYPE TEXT",
					"ALTER TABLE trending_dishes ALTER COLUMN name TYPE TEXT",
					"ALTER TABLE subscription_payments ALTER COLUMN stripe_payment_id TYPE TEXT"
				};

				for (String sql : tablesToFix) {
					try {
						jdbcTemplate.execute(sql);
					} catch (Exception colEx) {
						// Ignore if column doesn't exist or already correct type
					}
				}

				// Taxonomy Migration
				try {
					// 1. Create columns if they don't exist
					jdbcTemplate.execute("ALTER TABLE recipes ADD COLUMN IF NOT EXISTS category_id UUID");
					jdbcTemplate.execute("ALTER TABLE recipes ADD COLUMN IF NOT EXISTS cuisine_id UUID");

					// 1b. Add unique constraint to recipe_categories to prevent duplicates
					try {
						jdbcTemplate.execute("ALTER TABLE recipe_categories ADD CONSTRAINT unique_name_type UNIQUE (name, type)");
					} catch (Exception e) {
						// Constraint likely already exists
					}

					// 2. Populate recipe_categories from existing text
					jdbcTemplate.execute("INSERT INTO recipe_categories (id, name, type, created_at, updated_at) " +
						"SELECT gen_random_uuid(), category, 'CATEGORY', NOW(), NOW() FROM recipes " +
						"WHERE category IS NOT NULL AND category != '' " +
						"ON CONFLICT (name, type) DO NOTHING");
					
					jdbcTemplate.execute("INSERT INTO recipe_categories (id, name, type, created_at, updated_at) " +
						"SELECT gen_random_uuid(), cuisine, 'CUISINE', NOW(), NOW() FROM recipes " +
						"WHERE cuisine IS NOT NULL AND cuisine != '' " +
						"ON CONFLICT (name, type) DO NOTHING");

					// 3. Link recipes to categories
					jdbcTemplate.execute("UPDATE recipes r SET category_id = (SELECT id FROM recipe_categories rc WHERE rc.name = r.category AND rc.type = 'CATEGORY') WHERE category_id IS NULL AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='recipes' AND column_name='category')");
					jdbcTemplate.execute("UPDATE recipes r SET cuisine_id = (SELECT id FROM recipe_categories rc WHERE rc.name = r.cuisine AND rc.type = 'CUISINE') WHERE cuisine_id IS NULL AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='recipes' AND column_name='cuisine')");

					// 4. Drop legacy columns
					try {
						jdbcTemplate.execute("ALTER TABLE recipes DROP COLUMN IF EXISTS category");
						jdbcTemplate.execute("ALTER TABLE recipes DROP COLUMN IF EXISTS cuisine");
					} catch (Exception dropEx) {
						// Columns might be already dropped or have constraints
					}

					System.out.println("COOKED_DB_CLEANUP: Taxonomy migration completed and legacy columns dropped!");

				} catch (Exception migEx) {
					System.err.println("COOKED_DB_CLEANUP: Taxonomy migration (populating/linking) skipped or already done: " + migEx.getMessage());
				}

				// 5. Update categories with Cloudinary images from JSON - ALWAYS RUN THIS
				try {
					InputStream is = BackendApplication.class.getResourceAsStream("/taxonomy_images.json");
					if (is != null) {
						ObjectMapper mapper = new ObjectMapper();
						JsonNode root = mapper.readTree(is);
						JsonNode categories = root.get("categories");
						JsonNode cuisines = root.get("cuisines");

						// Comprehensive Standardization for Categories
						jdbcTemplate.execute("UPDATE recipe_categories SET name = 'Healthy Breakfasts' WHERE UPPER(TRIM(name)) IN ('BREAKFAST', 'MEAL PREP FAVORITES') AND type = 'CATEGORY'");
						jdbcTemplate.execute("UPDATE recipe_categories SET name = 'High Protein Picks' WHERE UPPER(TRIM(name)) IN ('HIGH PROTEIN', 'PROTEIN PLATES', 'HIGH PROTEIN, LOW CALORIE') AND type = 'CATEGORY'");
						jdbcTemplate.execute("UPDATE recipe_categories SET name = 'Rice & Grain' WHERE UPPER(TRIM(name)) = 'RICE & GRAIN DISHES' AND type = 'CATEGORY'");
						jdbcTemplate.execute("UPDATE recipe_categories SET name = 'Handheld / Street Food' WHERE UPPER(TRIM(name)) IN ('HANDHELD STREET FOOD', 'SIDES & SNACKS') AND type = 'CATEGORY'");
						jdbcTemplate.execute("UPDATE recipe_categories SET name = 'Stir Fry' WHERE UPPER(TRIM(name)) = 'STIR FRY SAUTÉ WOK' AND type = 'CATEGORY'");
						jdbcTemplate.execute("UPDATE recipe_categories SET name = '30-Minute Meals' WHERE UPPER(TRIM(name)) IN ('LUNCH', 'QUICK LUNCHES') AND type = 'CATEGORY'");
						jdbcTemplate.execute("UPDATE recipe_categories SET name = 'Plant-Based Essentials' WHERE UPPER(TRIM(name)) = 'VEGAN ESSENTIALS' AND type = 'CATEGORY'");
						jdbcTemplate.execute("UPDATE recipe_categories SET name = 'Pizza & Flatbreads' WHERE UPPER(TRIM(name)) = 'COMFORT FOOD' AND type = 'CATEGORY'");
						
						// Comprehensive Standardization for Cuisines - Removed incorrect raw SQL updates. TaxonomyServiceImpl handles merging safely.

						// Diagnostic log
						List<String> existing = jdbcTemplate.queryForList("SELECT name FROM recipe_categories LIMIT 50", String.class);
						System.out.println("DEBUG: Final Standardized categories in DB: " + existing);


						if (categories != null) {
							categories.fields().forEachRemaining(entry -> {
								int rows = jdbcTemplate.update("UPDATE recipe_categories SET image = ? WHERE UPPER(TRIM(name)) = UPPER(?) AND type = 'CATEGORY'", 
									entry.getValue().asText(), entry.getKey().trim());
								if (rows > 0) System.out.println("Updated Category image: " + entry.getKey());
							});
						}
						if (cuisines != null) {
							cuisines.fields().forEachRemaining(entry -> {
								int rows = jdbcTemplate.update("UPDATE recipe_categories SET image = ? WHERE UPPER(TRIM(name)) = UPPER(?) AND type = 'CUISINE'", 
									entry.getValue().asText(), entry.getKey().trim());
								if (rows > 0) System.out.println("Updated Cuisine image: " + entry.getKey());
							});
						}
						System.out.println("COOKED_DB_CLEANUP: Taxonomy images updated from JSON!");
					} else {
						System.err.println("COOKED_DB_CLEANUP: taxonomy_images.json NOT FOUND in classpath!");
					}
				} catch (Exception jsonEx) {
					System.err.println("COOKED_DB_CLEANUP: Taxonomy image update error: " + jsonEx.getMessage());
				}

				System.out.println("COOKED_DB_CLEANUP: Database constraints and schema fixed successfully!");
			} catch (Exception e) {
				System.err.println("COOKED_DB_CLEANUP: Database cleaning skipped or error: " + e.getMessage());
			}
		};
	}

}
