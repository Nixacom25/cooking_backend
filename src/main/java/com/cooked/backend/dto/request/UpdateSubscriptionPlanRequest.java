package com.cooked.backend.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateSubscriptionPlanRequest {
    @NotNull
    @Min(0)
    private BigDecimal monthlyPrice;

    @NotNull
    @Min(0)
    private BigDecimal yearlyPrice;

    @NotNull
    @Min(0)
    private Double yearlyDiscountPercentage;

    @NotNull
    @Min(0)
    private Integer trialDays;
}
