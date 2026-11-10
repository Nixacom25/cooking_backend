package com.cooked.backend.service;

import com.cooked.backend.dto.response.ArchiveItemResponse;
import com.cooked.backend.entity.User;

import java.util.List;
import java.util.UUID;

/** Everything deleted in Cooked (recipes, accounts...), listed on the admin Archive page. */
public interface ArchiveService {

    /** Keeps a trace of an account about to be deleted for good (masked email only). */
    void recordAccountDeletion(User user);

    /** Keeps a trace of something removed for good. */
    void record(String entityType, UUID entityId, String label, String details);

    List<ArchiveItemResponse> list();
}
