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

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Application is ready! Triggering explore recipes and categories seeder...");
        try {
            // exploreDataSeederService.seedExploreData();
            log.info("Explore recipes and categories seeding is disabled by user request.");
        } catch (Exception e) {
            log.error("CRITICAL: Failed to seed explore recipes and categories on startup", e);
        }
    }
}
