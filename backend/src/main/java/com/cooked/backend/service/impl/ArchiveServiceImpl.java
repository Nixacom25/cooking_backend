package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.ArchiveItemResponse;
import com.cooked.backend.entity.ArchiveEntry;
import com.cooked.backend.entity.Recipe;
import com.cooked.backend.entity.User;
import com.cooked.backend.repository.ArchiveEntryRepository;
import com.cooked.backend.repository.RecipeRepository;
import com.cooked.backend.service.ArchiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ArchiveServiceImpl implements ArchiveService {

    private final ArchiveEntryRepository entries;
    private final RecipeRepository recipes;
    private final com.cooked.backend.repository.UserRepository users;

    @Override
    @Transactional
    public void recordAccountDeletion(User user) {
        if (user == null) return;
        String actor = user.getDeletionRequestedBy() != null ? user.getDeletionRequestedBy() : currentEmail();
        boolean self = "self".equals(actor) || (actor != null && actor.equalsIgnoreCase(user.getEmail()));
        String name = user.getFirstname() == null || user.getFirstname().isBlank() ? "Compte" : user.getFirstname();
        entries.save(ArchiveEntry.builder()
                .entityType(user.getRole() == null ? "CLIENT" : user.getRole().name())
                .entityId(user.getId())
                .label(name + " · " + maskEmail(user.getEmail()))
                .details(self ? "Compte supprimé par l'utilisateur" : "Compte supprimé depuis le backoffice")
                .deletedBy(self ? "self" : actor)
                .build());
    }

    @Override
    @Transactional
    public void record(String entityType, UUID entityId, String label, String details) {
        entries.save(ArchiveEntry.builder()
                .entityType(entityType)
                .entityId(entityId)
                .label(label == null || label.isBlank() ? "—" : truncate(label, 300))
                .details(details)
                .deletedBy(currentEmail())
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArchiveItemResponse> list() {
        List<ArchiveItemResponse> items = new ArrayList<>();
        for (Recipe r : recipes.findArchived()) items.add(fromRecipe(r));
        for (ArchiveEntry e : entries.findTop500ByOrderByCreatedAtDesc()) items.add(fromEntry(e));
        for (User u : users.findAllByDeletionRequestedAtIsNotNullOrderByDeletionRequestedAtAsc()) items.add(fromPendingAccount(u));
        items.sort(Comparator.comparing(ArchiveItemResponse::getDeletedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return items;
    }

    static ArchiveItemResponse fromPendingAccount(User u) {
        boolean self = "self".equals(u.getDeletionRequestedBy());
        String name = u.getFirstname() == null || u.getFirstname().isBlank() ? "Compte" : u.getFirstname();
        return ArchiveItemResponse.builder()
                .id(u.getId())
                .kind(u.getRole() == null ? "CLIENT" : u.getRole().name())
                .state("PENDING_DELETION")
                .label(name + " · " + maskEmail(u.getEmail()))
                .details(u.getDeletionRequestedAt().plusDays(30).toLocalDate().toString())
                .deletedBy(self ? null : u.getDeletionRequestedBy())
                .deletedByRole(self ? "SELF" : "STAFF")
                .deletedAt(u.getDeletionRequestedAt())
                .build();
    }

    static ArchiveItemResponse fromRecipe(Recipe r) {
        if (r.getOrphanedAt() != null) {
            return ArchiveItemResponse.builder()
                    .id(r.getId()).kind("RECIPE").state("ORPHANED").label(r.getName())
                    .details(r.getCuisine() != null ? r.getCuisine().getName() : null).image(r.getImage())
                    .origin(String.valueOf(r.getPreviousOrigin() != null ? r.getPreviousOrigin() : r.getOrigin()))
                    .deletedBy(r.getOwnerDeletedBy()).deletedByRole(r.getOwnerDeletedRole()).deletedAt(r.getOrphanedAt())
                    .canKeep(true).canPurge(true)
                    .build();
        }
        boolean trash = r.isDeleted();
        boolean byOwner = r.getOwnerDeletedAt() != null;
        LocalDateTime at = trash ? firstNonNull(r.getDeletedAt(), r.getUpdatedAt()) : r.getOwnerDeletedAt();
        String role = trash ? "STAFF" : r.getOwnerDeletedRole();
        String by = trash ? r.getDeletedBy() : r.getOwnerDeletedBy();
        if (trash && byOwner && by != null && by.equalsIgnoreCase(r.getOwnerDeletedBy())) role = r.getOwnerDeletedRole();
        return ArchiveItemResponse.builder()
                .id(r.getId())
                .kind("RECIPE")
                .state(trash ? "TRASH" : "TO_REWORK")
                .label(r.getName())
                .details(r.getCuisine() != null ? r.getCuisine().getName() : null)
                .image(r.getImage())
                .origin(String.valueOf(r.getPreviousOrigin() != null ? r.getPreviousOrigin() : r.getOrigin()))
                .deletedBy(by)
                .deletedByRole(role)
                .deletedAt(at)
                .canRestore(trash)
                .canPurge(true)
                .build();
    }

    static ArchiveItemResponse fromEntry(ArchiveEntry e) {
        return ArchiveItemResponse.builder()
                .id(e.getId())
                .kind(e.getEntityType())
                .state("REMOVED")
                .label(e.getLabel())
                .details(e.getDetails())
                .deletedBy("self".equals(e.getDeletedBy()) ? null : e.getDeletedBy())
                .deletedByRole("self".equals(e.getDeletedBy()) ? "SELF" : "STAFF")
                .deletedAt(e.getCreatedAt())
                .build();
    }

    static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "—";
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        return (local.length() <= 2 ? local.charAt(0) : local.substring(0, 2)) + "***" + email.substring(at);
    }

    private static String currentEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || "anonymousUser".equals(auth.getName()) ? null : auth.getName();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static <T> T firstNonNull(T a, T b) {
        return a != null ? a : b;
    }
}
