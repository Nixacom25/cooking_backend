package com.cooked.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "paywall_variants")
public class PaywallVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String variantKey; // "A" or "B"

    private String title;
    private String subtitle;
    
    private String monthlyPriceLabel;
    private String yearlyPriceLabel;
    
    private String ctaText;
    private String discountLabel; // ex: "-20% Today"
    
    private String backgroundImage; // URL distante
    
    @Column(columnDefinition = "TEXT")
    private String featuresJson; // Liste des bénéfices en JSON

    public PaywallVariant() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getVariantKey() { return variantKey; }
    public void setVariantKey(String variantKey) { this.variantKey = variantKey; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public String getMonthlyPriceLabel() { return monthlyPriceLabel; }
    public void setMonthlyPriceLabel(String monthlyPriceLabel) { this.monthlyPriceLabel = monthlyPriceLabel; }
    public String getYearlyPriceLabel() { return yearlyPriceLabel; }
    public void setYearlyPriceLabel(String yearlyPriceLabel) { this.yearlyPriceLabel = yearlyPriceLabel; }
    public String getCtaText() { return ctaText; }
    public void setCtaText(String ctaText) { this.ctaText = ctaText; }
    public String getDiscountLabel() { return discountLabel; }
    public void setDiscountLabel(String discountLabel) { this.discountLabel = discountLabel; }
    public String getBackgroundImage() { return backgroundImage; }
    public void setBackgroundImage(String backgroundImage) { this.backgroundImage = backgroundImage; }
    public String getFeaturesJson() { return featuresJson; }
    public void setFeaturesJson(String featuresJson) { this.featuresJson = featuresJson; }
}
