package com.cooked.backend.dto.response;

import com.cooked.backend.entity.CostCategory;
import com.cooked.backend.entity.CostFrequency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostEntryResponse {
    private UUID id;
    private String provider;
    private CostCategory category;
    private String categoryLabel;
    private BigDecimal amount;
    private String currency;
    private BigDecimal amountUsd;
    private CostFrequency frequency;
    private LocalDate startDate;
    private LocalDate endDate;
    private String notes;
    private String createdBy;
}
