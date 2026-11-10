package com.cooked.backend.service;

import com.cooked.backend.dto.response.MessageResponse;

import java.util.UUID;

/**
 * Recipe deletion rules:
 * - nobody but the owner can delete a recipe that belongs to a client or creator, admins included;
 * - the backoffice (admins and interns) can only delete owner-less Explore recipes; they go to the archive (restorable);
 * - Scan, Import, Suggested and Manual recipes can only be deleted by their owner: the recipe leaves the
 *   owner's library, becomes an Explore recipe to rework and admins are notified. Validating the reworked
 *   recipe publishes it and takes it out of the archive;
 * - when an owner's account is deleted for good (30 days after the request), their recipes wait in the archive
 *   for an admin to keep them (they become Explore recipes to rework) or delete them for good;
 * - an admin can delete an archived, owner-less recipe for good.
 */
public interface RecipeDeletionService {

    MessageResponse deleteByOwner(UUID recipeId, String ownerEmail);

    void deleteFromBackoffice(UUID recipeId, String staffEmail);

    void restore(UUID recipeId, String staffEmail);

    void purge(UUID recipeId, String adminEmail);

    /** Recipe of a deleted account kept by an admin: becomes an Explore recipe for the interns to rework. */
    void keepOrphan(UUID recipeId, String adminEmail);
}
