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
				// Drop the specific phone unique constraint found in Neon screenshot
				jdbcTemplate.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS uk_du5v5sr43g5bfnji4vb8hg5s3");
				// Also drop the duplicate email constraint if it exists
				jdbcTemplate.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS uk_6dotkott2kjsp8vw4d0m25fb7");
				System.out.println("COOKED_DB_CLEANUP: Database constraints cleaned successfully!");
			} catch (Exception e) {
				System.err.println("COOKED_DB_CLEANUP: Database cleaning skipped or error: " + e.getMessage());
			}
		};
	}

}
