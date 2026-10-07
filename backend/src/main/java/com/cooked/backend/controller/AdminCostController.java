package com.cooked.backend.controller;

import com.cooked.backend.dto.request.CreateCostRequest;
import com.cooked.backend.dto.request.UpdateProviderBudgetRequest;
import com.cooked.backend.dto.response.CostEntryResponse;
import com.cooked.backend.dto.response.CostOverviewResponse;
import com.cooked.backend.dto.response.FeatureCostResponse;
import com.cooked.backend.service.FeatureCostService;
import com.cooked.backend.dto.response.ProviderCostDetailResponse;
import com.cooked.backend.service.AdminCostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Cost Center. HTTP only: the work is in {@link AdminCostService}. */
@RestController
@RequestMapping("/api/admin/costs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Costs", description = "What Cooked costs to run: manual costs, budgets, credits and billing APIs")
public class AdminCostController {

    private final AdminCostService costService;
    private final FeatureCostService featureCostService;

    @Operation(summary = "Totals, daily spend, categories, providers and alerts (days: 1-90, default 30)")
    @GetMapping("/overview")
    public ResponseEntity<CostOverviewResponse> overview(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(costService.overview(days));
    }

    @Operation(summary = "Estimated cost per feature: AI spend split by weighted call volume (days: 1-45)")
    @GetMapping("/features")
    public ResponseEntity<FeatureCostResponse> features(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(featureCostService.features(days));
    }

    @Operation(summary = "One provider: daily spend and line items")
    @GetMapping("/providers/{provider}")
    public ResponseEntity<ProviderCostDetailResponse> provider(@PathVariable String provider, @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(costService.provider(provider, days));
    }

    @Operation(summary = "Set a provider's monthly budget and prepaid credits")
    @PutMapping("/providers/{provider}/budget")
    public ResponseEntity<CostOverviewResponse.ProviderRow> budget(@PathVariable String provider, @Valid @RequestBody UpdateProviderBudgetRequest request) {
        return ResponseEntity.ok(costService.updateBudget(provider, request));
    }

    @Operation(summary = "Manual costs")
    @GetMapping("/entries")
    public ResponseEntity<List<CostEntryResponse>> entries() {
        return ResponseEntity.ok(costService.entries());
    }

    @Operation(summary = "Add a manual cost (vendors without a billing API)")
    @PostMapping("/entries")
    public ResponseEntity<CostEntryResponse> add(@Valid @RequestBody CreateCostRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(costService.addEntry(request, auth.getName()));
    }

    @Operation(summary = "Delete a manual cost")
    @DeleteMapping("/entries/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        costService.deleteEntry(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Pull recent spend from configured billing APIs now")
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Integer>> sync() {
        return ResponseEntity.ok(Map.of("rows", costService.syncProviders()));
    }
}
