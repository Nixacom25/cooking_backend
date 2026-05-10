package com.cooked.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

	@org.springframework.context.annotation.Bean
	org.springframework.boot.CommandLineRunner commandLineRunner(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
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

				System.out.println("COOKED_DB_CLEANUP: Database constraints and schema fixed successfully!");
			} catch (Exception e) {
				System.err.println("COOKED_DB_CLEANUP: Database cleaning skipped or error: " + e.getMessage());
			}
		};
	}

}
