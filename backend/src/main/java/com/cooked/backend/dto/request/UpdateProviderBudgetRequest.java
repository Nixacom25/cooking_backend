package com.cooked.backend.dto.request;

import com.cooked.backend.entity.CostCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** Null fields are cleared. */
@Data
public class UpdateProviderBudgetRequest {
    private CostCategory category;
    @DecimalMin("0")
    private BigDecimal monthlyBudgetUsd;
    @DecimalMin("0")
    private BigDecimal creditBalance;
    @Size(max = 40)
    private String creditUnit;
}
