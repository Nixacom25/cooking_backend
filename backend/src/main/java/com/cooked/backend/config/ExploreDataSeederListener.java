package com.cooked.backend.config;

import com.cooked.backend.service.ExploreDataSeederService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExploreDataSeederListener {

    private final ExploreDataSeederService exploreDataSeederService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final org.springframework.cache.CacheManager cacheManager;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Application is ready! Running custom database migrations...");
        try {
            log.info("Evicting Explore and Popular caches on startup...");
            for (String cacheName : java.util.Arrays.asList("exploreRecipes", "popularRecipes", "explore_cuisines", "explore_categories")) {
                org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    cache.clear();
                    log.info("Cleared cache: {}", cacheName);
                }
            }
        } catch (Exception e) {
            log.error("Failed to clear caches on startup: " + e.getMessage());
        }

        try {
            // Create migrations table if not exists
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS schema_migrations_cooked (version VARCHAR(255) PRIMARY KEY)");
            
            // Check if reset migration has run
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schema_migrations_cooked WHERE version = 'reset_explore_status_to_false_2026'",
                Integer.class
            );
            
            if (count == null || count == 0) {
                log.info("Running one-time migration: Setting all EXPLORE recipes status to false...");
                jdbcTemplate.execute("UPDATE recipes SET status = false WHERE origin = 'EXPLORE'");
                jdbcTemplate.execute("INSERT INTO schema_migrations_cooked (version) VALUES ('reset_explore_status_to_false_2026')");
                log.info("Migration completed successfully.");
            } else {
                log.info("One-time EXPLORE status reset migration has already run. Skipping.");
            }
        } catch (Exception e) {
            log.error("Failed to run custom explore status migration", e);
        }

        log.info("Triggering explore recipes and categories seeder...");
        try {
            // exploreDataSeederService.seedExploreData();
            log.info("Explore recipes and categories seeding is disabled by user request.");
        } catch (Exception e) {
            log.error("CRITICAL: Failed to seed explore recipes and categories on startup", e);
        }
    }
}
