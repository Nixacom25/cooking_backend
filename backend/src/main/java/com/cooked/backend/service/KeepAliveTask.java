package com.cooked.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class KeepAliveTask {

    private final RestTemplate restTemplate;

    @Value("${AI_API_BASE_URL:https://recipe.markhorsystems.com}")
    private String aiBaseUrl;

    // Use the backend's own public URL to keep it awake
    @Value("${APP_PUBLIC_URL:https://cooked-backend-latest.onrender.com}")
    private String backendUrl;

    public KeepAliveTask() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Pings both services every 10 minutes to prevent Render spin-down.
     * Render free tier spins down after 15 minutes of inactivity.
     */
    @Scheduled(fixedRate = 600000) // 10 minutes in milliseconds
    public void keepServicesAlive() {
        log.info("Running Keep-Alive task to prevent Render spin-down...");
        
        // 1. Ping AI Service
        try {
            String aiHealthUrl = aiBaseUrl + "/health";
            restTemplate.getForObject(aiHealthUrl, String.class);
            log.info("AI Service pinged successfully at: {}", aiHealthUrl);
        } catch (Exception e) {
            log.warn("Could not ping AI Service: {}", e.getMessage());
        }

        // 2. Ping Backend Service
        try {
            String backendHealthUrl = backendUrl + "/actuator/health";
            restTemplate.getForObject(backendHealthUrl, String.class);
            log.info("Backend Service pinged successfully at: {}", backendHealthUrl);
        } catch (Exception e) {
            log.warn("Could not ping Backend Service (this might be normal if /health is not public): {}", e.getMessage());
        }
    }
}
