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
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000); // 10s
        factory.setReadTimeout(120000);   // 120s for AI
        return new RestTemplate(factory);
    }

    @Bean
    @Transactional
    public org.springframework.boot.CommandLineRunner dataInitializer(
            com.cooked.backend.service.ExploreDataSeederService exploreDataSeederService,
            jakarta.persistence.EntityManager entityManager) {
        return args -> {
            // Drop ALL unique constraints on recipes, cookbooks, ingredients, etc. to allow duplicates as requested
            try {
                String sql = "DO $$ " +
                            "DECLARE r RECORD; " +
                            "BEGIN " +
                            "  -- Supprimer les contraintes d'unicité sur les tables clés\n" +
                            "  FOR r IN (SELECT conname, relname FROM pg_constraint c JOIN pg_class cl ON c.conrelid = cl.oid WHERE cl.relname IN ('recipes', 'cookbooks', 'ingredients', 'recipe_data') AND contype = 'u') " +
                            "  LOOP " +
                            "    EXECUTE 'ALTER TABLE ' || r.relname || ' DROP CONSTRAINT IF EXISTS ' || r.conname; " +
                            "  END LOOP; " +
                            "  -- Supprimer les index uniques\n" +
                            "  FOR r IN (SELECT indexname, tablename FROM pg_indexes WHERE tablename IN ('recipes', 'cookbooks', 'ingredients', 'recipe_data') AND indexdef LIKE '%UNIQUE INDEX%') " +
                            "  LOOP " +
                            "    EXECUTE 'DROP INDEX IF EXISTS ' || r.indexname; " +
                            "  END LOOP; " +
                            "END $$;";
                entityManager.createNativeQuery(sql).executeUpdate();
                log.info("Successfully dropped all unique constraints from recipes, cookbooks, ingredients and recipe_data tables");
            } catch (Exception e) {
                log.warn("Could not drop constraints: {}", e.getMessage());
            }
            
            exploreDataSeederService.seedExploreData();
        };
    }
}
