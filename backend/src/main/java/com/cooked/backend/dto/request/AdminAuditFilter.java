package com.cooked.backend.dto.request;

/**
 * Filters of the admin Audit log (intern activity). All optional.
 *
 * @param person editor email
 * @param area   entity type (RECIPE, INGREDIENT…)
 * @param action activity title, exact
 * @param days   only the last N days
 * @param q      text in title, message or editor name / email
 */
public record AdminAuditFilter(String person, String area, String action, Integer days, String q) {
}
