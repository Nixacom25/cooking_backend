package com.cooked.backend.controller;

import com.cooked.backend.service.KpiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class DashboardController {

    private final KpiService kpiService;

    public DashboardController(KpiService kpiService) {
        this.kpiService = kpiService;
    }

    @GetMapping("/api/kpi/global")
    public ResponseEntity<Map<String, Object>> getGlobalKpis() {
        return ResponseEntity.ok(kpiService.getGlobalKpis());
    }
}
