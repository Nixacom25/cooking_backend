package com.cooked.backend.dto.response;

/** notStarted + inProgress = moved. */
public record BatchReassignResponse(int moved, int notStarted, int inProgress, String from, String to) {
}
