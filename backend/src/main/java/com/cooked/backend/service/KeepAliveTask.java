package com.cooked.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class KeepAliveTask {
    private static final Logger log = LoggerFactory.getLogger(KeepAliveTask.class);

    private final RestTemplate restTemplate;

    @Value("${AI_API_BASE_URL:https://recipe.markhorsystems.com}")
    private String aiBaseUrl;

    @Value("${APP_PUBLIC_URL:https://cooked-backend-latest.onrender.com}")
    private String backendUrl;

    public KeepAliveTask() {
        this.restTemplate = new RestTemplate();
    }

    @Scheduled(fixedRate = 600000)
    public void keepServicesAlive() {
        log.info("Running Keep-Alive task to prevent Render spin-down...");
        
        try {
            String aiHealthUrl = aiBaseUrl + "/health";
            restTemplate.getForObject(aiHealthUrl, String.class);
            log.info("AI Service pinged successfully at: {}", aiHealthUrl);
        } catch (Exception e) {
            log.warn("Could not ping AI Service: {}", e.getMessage());
        }

        try {
            String backendHealthUrl = backendUrl + "/actuator/health";
            restTemplate.getForObject(backendHealthUrl, String.class);
            log.info("Backend Service pinged successfully at: {}", backendHealthUrl);
        } catch (Exception e) {
            log.warn("Could not ping Backend Service (this might be normal if /health is not public): {}", e.getMessage());
        }
    }
}
