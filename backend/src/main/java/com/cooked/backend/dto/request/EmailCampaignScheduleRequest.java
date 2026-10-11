package com.cooked.backend.dto.request;

import java.time.OffsetDateTime;

/** sendAt null = send now; confirmed = "I checked the content and the audience". */
public record EmailCampaignScheduleRequest(OffsetDateTime sendAt, boolean confirmed) {
}
