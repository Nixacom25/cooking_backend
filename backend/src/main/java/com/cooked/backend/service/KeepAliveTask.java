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
    private final ActivityTracker activityTracker;
    private long lastPingTime = 0;

    @Value("${AI_API_BASE_URL:https://recipe.markhorsystems.com}")
    private String aiBaseUrl;

    @Value("${APP_PUBLIC_URL:https://cooked-backend-latest.onrender.com}")
    private String backendUrl;

    public KeepAliveTask(ActivityTracker activityTracker) {
        this.restTemplate = new RestTemplate();
        this.activityTracker = activityTracker;
    }

    @Scheduled(fixedRate = 60000) // Check every minute
    public void keepServicesAlive() {
        long now = System.currentTimeMillis();
        long tenMinutesMillis = 600000;

        // Condition 1: Must be inactive for at least 10 minutes
        if (!activityTracker.hasBeenInactiveFor(tenMinutesMillis)) {
            return; // Still active, wait for inactivity
        }

        // Condition 2: Don't ping more than once every 10 minutes
        if (now - lastPingTime < tenMinutesMillis) {
            return;
        }

        log.info("App has been inactive for 10 minutes. Running Keep-Alive task to prevent Render spin-down...");
        lastPingTime = now;
        
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
