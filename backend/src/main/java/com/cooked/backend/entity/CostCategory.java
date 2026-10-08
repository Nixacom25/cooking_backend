package com.cooked.backend.entity;

/** Cost Center categories (labels match the backoffice). */
public enum CostCategory {
    CONTENT_IMAGES("Content & images"),
    INFRASTRUCTURE("Infrastructure"),
    AI_APIS("AI & APIs"),
    ANALYTICS_TOOLING("Analytics & tooling"),
    GROWTH_TOOLS("Growth tools"),
    /** Paid acquisition (ads): used for CAC and ROAS on Acquisition. */
    ADVERTISING("Advertising"),
    LEGAL_COMPLIANCE("Legal & compliance"),
    OTHER("Other");

    private final String label;

    CostCategory(String label) { this.label = label; }

    public String getLabel() { return label; }
}
