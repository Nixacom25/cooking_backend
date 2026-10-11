package com.cooked.backend.dto.response;

/** An audience: everyone in it, and who would actually get the email (frequency cap applied). */
public record EmailAudienceCount(String key, String label, String description, long people, long afterExclusions) {
}
