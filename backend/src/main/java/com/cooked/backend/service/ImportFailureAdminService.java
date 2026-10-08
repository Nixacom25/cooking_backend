package com.cooked.backend.service;

import com.cooked.backend.dto.response.ImportRetryResponse;

import java.util.UUID;

/** Admin actions on failed imports. */
public interface ImportFailureAdminService {

    /** Marks the failure as handled. */
    void resolve(UUID eventId);

    /** Runs the import again for the same user and URL; resolves the failure when it works. */
    ImportRetryResponse retry(UUID eventId);
}
