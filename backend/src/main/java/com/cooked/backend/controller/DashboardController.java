package com.cooked.backend.controller;

import com.cooked.backend.service.KpiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final KpiService kpiService;

    @GetMapping("/dashboard")
    public ResponseEntity<String> getDashboard() {
        try {
            String path = "../../landing_page/dashboard.html"; // Chemin relatif vers le dossier landing_page
            return ResponseEntity.ok(new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path))));
        } catch (Exception e) {
            return ResponseEntity.status(404).body("Dashboard file not found in landing_page folder.");
        }
    }

    @GetMapping("/api/kpi/global")
    public ResponseEntity<Map<String, Object>> getGlobalKpis() {
        return ResponseEntity.ok(kpiService.getGlobalKpis());
    }
}
