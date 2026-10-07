package com.cooked.backend.entity;

/** How a manual cost is spread over time. */
public enum CostFrequency {
    /** Amount per calendar month, spread evenly over its days. */
    MONTHLY,
    /** Amount per year, spread evenly over its days. */
    ANNUAL,
    /** Whole amount on the start date. */
    ONE_TIME
}
