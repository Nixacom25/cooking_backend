package com.cooked.backend.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

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

    public UpdateSubscriptionPlanRequest() {}

    public BigDecimal getMonthlyPrice() { return monthlyPrice; }
    public void setMonthlyPrice(BigDecimal monthlyPrice) { this.monthlyPrice = monthlyPrice; }
    public BigDecimal getYearlyPrice() { return yearlyPrice; }
    public void setYearlyPrice(BigDecimal yearlyPrice) { this.yearlyPrice = yearlyPrice; }
    public Double getYearlyDiscountPercentage() { return yearlyDiscountPercentage; }
    public void setYearlyDiscountPercentage(Double yearlyDiscountPercentage) { this.yearlyDiscountPercentage = yearlyDiscountPercentage; }
    public Integer getTrialDays() { return trialDays; }
    public void setTrialDays(Integer trialDays) { this.trialDays = trialDays; }
}
