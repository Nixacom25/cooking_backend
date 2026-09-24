package com.cooked.backend.dto.request;

import lombok.Data;

/**
 * Partial update: any field left null is left unchanged, so the mobile app
 * only needs to send the toggle the user actually flipped.
 */
@Data
public class UpdateNotificationPreferencesRequest {
    private Boolean pushEnabled;
    private Boolean pushRemindersEnabled;
    private Boolean pushNewsOffersEnabled;
}
