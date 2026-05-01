package com.cooked.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

@Configuration
@Slf4j
public class AppConfig {
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    @Transactional
    public org.springframework.boot.CommandLineRunner dataInitializer(
            com.cooked.backend.service.ExploreDataSeederService exploreDataSeederService,
            jakarta.persistence.EntityManager entityManager) {
        return args -> {
            // Drop ALL unique constraints on recipes table to allow recipes with same name
            try {
                String sql = "DO $$ " +
                            "DECLARE r RECORD; " +
                            "BEGIN " +
                            "  -- Supprimer les contraintes d'unicité\n" +
                            "  FOR r IN (SELECT conname FROM pg_constraint WHERE conrelid = 'recipes'::regclass AND contype = 'u') " +
                            "  LOOP " +
                            "    EXECUTE 'ALTER TABLE recipes DROP CONSTRAINT IF EXISTS ' || r.conname; " +
                            "  END LOOP; " +
                            "  -- Supprimer les index uniques (parfois créés sans contrainte explicite)\n" +
                            "  FOR r IN (SELECT indexname FROM pg_indexes WHERE tablename = 'recipes' AND indexdef LIKE '%UNIQUE INDEX%') " +
                            "  LOOP " +
                            "    EXECUTE 'DROP INDEX IF EXISTS ' || r.indexname; " +
                            "  END LOOP; " +
                            "END $$;";
                entityManager.createNativeQuery(sql).executeUpdate();
                log.info("Successfully dropped all unique constraints from recipes table");
            } catch (Exception e) {
                log.warn("Could not drop constraints: {}", e.getMessage());
            }
            
            exploreDataSeederService.seedExploreData();
        };
    }
}
