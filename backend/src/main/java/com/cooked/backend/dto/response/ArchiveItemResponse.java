package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One row of the admin Archive page. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveItemResponse {
    private UUID id;
    /** RECIPE, CLIENT, CREATOR, ADMIN or EDITOR. */
    private String kind;
    /**
     * TO_REWORK (deleted by its owner, waiting for an intern), ORPHANED (its owner's account was deleted: keep or delete),
     * TRASH (restorable), PENDING_DELETION (account deleted in 30 days unless its owner logs in) or REMOVED (gone for good).
     */
    private String state;
    private String label;
    private String details;
    private String image;
    /** Origin before the owner deleted it (SCAN, IMPORT, SUGGESTED, MANUAL), or the current origin. */
    private String origin;
    private String deletedBy;
    /** CLIENT, CREATOR, STAFF or SELF. */
    private String deletedByRole;
    private LocalDateTime deletedAt;
    private boolean canRestore;
    private boolean canPurge;
    /** Recipe of a deleted account: can be kept (becomes an Explore recipe to rework). */
    private boolean canKeep;
}
