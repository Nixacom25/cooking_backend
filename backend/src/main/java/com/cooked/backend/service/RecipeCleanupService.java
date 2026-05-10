package com.cooked.backend.service;

import com.cooked.backend.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class RecipeCleanupService {
    private static final Logger log = LoggerFactory.getLogger(RecipeCleanupService.class);

    private final RecipeRepository recipeRepository;

    /**
     * Runs every day at 1:00 AM to delete expired suggested recipes.
     */
    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void cleanupExpiredSuggestions() {
        log.info("Starting cleanup of expired suggested recipes...");
        try {
            int deletedCount = recipeRepository.deleteExpiredSuggestedRecipes(com.cooked.backend.entity.RecipeOrigin.SUGGESTED, LocalDateTime.now());
            log.info("Finished cleanup. Deleted {} expired suggestions.", deletedCount);
        } catch (Exception e) {
            log.error("Error during recipe cleanup", e);
        }
    }
}
