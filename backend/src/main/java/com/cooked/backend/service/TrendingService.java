package com.cooked.backend.service;

import com.cooked.backend.entity.TrendingDish;
import com.cooked.backend.repository.TrendingDishRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class TrendingService {
    private static final Logger log = LoggerFactory.getLogger(TrendingService.class);

    private final TrendingDishRepository trendingDishRepository;
    private final AiService aiService;

    @PostConstruct
    public void init() {
        if (trendingDishRepository.count() == 0) {
            log.info("No trending dishes found. Generating initial batch...");
            generateDailyTrendingDishes();
        }
    }

    @org.springframework.cache.annotation.CacheEvict(value = "trendingDishes", allEntries = true)
    @Scheduled(cron = "0 0 0 * * ?") // Runs every day at 00:00
    public void generateDailyTrendingDishes() {
        log.info("Starting daily trending dishes generation...");
        List<String> newDishes = aiService.generateTrendingDishes();
        if (newDishes != null && !newDishes.isEmpty()) {
            trendingDishRepository.deleteAll(); // Clear old trending dishes
            
            List<TrendingDish> entities = newDishes.stream()
                    .map(name -> TrendingDish.builder().name(name).build())
                    .collect(Collectors.toList());
            
            trendingDishRepository.saveAll(entities);
            log.info("Successfully saved {} trending dishes.", entities.size());
        } else {
            log.warn("AI failed to generate trending dishes.");
        }
    }

    @org.springframework.cache.annotation.Cacheable(value = "trendingDishes")
    public List<String> getTrendingDishes() {
        return trendingDishRepository.findAll().stream()
                .map(TrendingDish::getName)
                .collect(Collectors.toList());
    }
}
