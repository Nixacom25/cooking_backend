package com.cooked.backend.service;

import com.cooked.backend.dto.request.UpdateWorkspaceSettingsRequest;
import com.cooked.backend.dto.response.WorkspaceSettingsResponse;
import com.cooked.backend.entity.WorkspaceSettings;

public interface WorkspaceSettingsService {

    WorkspaceSettingsResponse get();

    WorkspaceSettingsResponse update(UpdateWorkspaceSettingsRequest request);

    /** Current settings for internal use (defaults when never saved). */
    WorkspaceSettings current();

    void markDailySummarySent(java.time.LocalDate day);

    void markWeeklyReviewSent(java.time.LocalDate day);
}
