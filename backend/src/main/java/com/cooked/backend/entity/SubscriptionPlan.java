package com.cooked.backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "subscription_plans")
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private BigDecimal monthlyPrice;

    @Column(nullable = false)
    private BigDecimal yearlyPrice;

    @Column(nullable = false)
    private Double yearlyDiscountPercentage;

    @Column(nullable = false)
    private Integer trialDays;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public BigDecimal getMonthlyPrice() { return monthlyPrice; }
    public void setMonthlyPrice(BigDecimal monthlyPrice) { this.monthlyPrice = monthlyPrice; }
    public BigDecimal getYearlyPrice() { return yearlyPrice; }
    public void setYearlyPrice(BigDecimal yearlyPrice) { this.yearlyPrice = yearlyPrice; }
    public Double getYearlyDiscountPercentage() { return yearlyDiscountPercentage; }
    public void setYearlyDiscountPercentage(Double yearlyDiscountPercentage) { this.yearlyDiscountPercentage = yearlyDiscountPercentage; }
    public Integer getTrialDays() { return trialDays; }
    public void setTrialDays(Integer trialDays) { this.trialDays = trialDays; }

    public static SubscriptionPlanBuilder builder() {
        return new SubscriptionPlanBuilder();
    }

    public static class SubscriptionPlanBuilder {
        private final SubscriptionPlan plan = new SubscriptionPlan();

        public SubscriptionPlanBuilder id(UUID id) { plan.setId(id); return this; }
        public SubscriptionPlanBuilder monthlyPrice(BigDecimal monthlyPrice) { plan.setMonthlyPrice(monthlyPrice); return this; }
        public SubscriptionPlanBuilder yearlyPrice(BigDecimal yearlyPrice) { plan.setYearlyPrice(yearlyPrice); return this; }
        public SubscriptionPlanBuilder yearlyDiscountPercentage(Double yearlyDiscountPercentage) { plan.setYearlyDiscountPercentage(yearlyDiscountPercentage); return this; }
        public SubscriptionPlanBuilder trialDays(Integer trialDays) { plan.setTrialDays(trialDays); return this; }

        public SubscriptionPlan build() {
            return plan;
        }
    }
}
