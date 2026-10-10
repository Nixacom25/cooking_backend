package com.cooked.backend.dto.response;

/** One correction reason (stored key) with how often it was used and its share in %. */
public record CorrectionReasonShare(String reason, long count, double share) {
}
