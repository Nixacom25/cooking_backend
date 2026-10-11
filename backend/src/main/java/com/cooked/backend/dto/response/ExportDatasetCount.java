package com.cooked.backend.dto.response;

/** A dataset that can be exported and how many rows the chosen period holds. */
public record ExportDatasetCount(String key, String label, String description, long rows) {
}
