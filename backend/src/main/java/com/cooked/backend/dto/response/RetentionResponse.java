package com.cooked.backend.dto.response;

import java.util.List;

/**
 * Retention of users who signed up in the window, grouped by one dimension.
 * dN = % of the group's eligible users (signed up at least N days ago) who
 * opened the app on day N after signing up or later; null when nobody is eligible yet.
 */
public record RetentionResponse(String by, int days, List<Group> groups) {

    public record Group(String label, long users, Double d1, Double d7, Double d14, Double d30) {
    }
}
