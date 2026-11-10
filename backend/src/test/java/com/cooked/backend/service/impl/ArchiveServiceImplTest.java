package com.cooked.backend.service.impl;

import com.cooked.backend.entity.ArchiveEntry;
import com.cooked.backend.entity.Recipe;
import com.cooked.backend.entity.RecipeOrigin;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ArchiveServiceImplTest {

    @Test
    void masksEmails() {
        assertEquals("aw***@gmail.com", ArchiveServiceImpl.maskEmail("awa.diop@gmail.com"));
        assertEquals("a***@x.sn", ArchiveServiceImpl.maskEmail("a@x.sn"));
        assertEquals("—", ArchiveServiceImpl.maskEmail(null));
    }

    @Test
    void ownerDeletedRecipeIsToRework() {
        Recipe r = Recipe.builder().name("Yassa").origin(RecipeOrigin.EXPLORE).previousOrigin(RecipeOrigin.SCAN)
                .ownerDeletedAt(LocalDateTime.now()).ownerDeletedBy("awa@mail.com").ownerDeletedRole("CLIENT").build();
        var item = ArchiveServiceImpl.fromRecipe(r);
        assertEquals("TO_REWORK", item.getState());
        assertEquals("SCAN", item.getOrigin());
        assertEquals("CLIENT", item.getDeletedByRole());
        assertFalse(item.isCanRestore());
        assertTrue(item.isCanPurge());
    }

    @Test
    void trashedRecipeCanBeRestored() {
        Recipe r = Recipe.builder().name("Mafé").origin(RecipeOrigin.EXPLORE).isDeleted(true)
                .deletedAt(LocalDateTime.now()).deletedBy("intern@cooked.app").build();
        var item = ArchiveServiceImpl.fromRecipe(r);
        assertEquals("TRASH", item.getState());
        assertEquals("STAFF", item.getDeletedByRole());
        assertTrue(item.isCanRestore());
    }

    @Test
    void selfDeletedAccountHidesTheActor() {
        var item = ArchiveServiceImpl.fromEntry(ArchiveEntry.builder().entityType("CLIENT").label("Awa · aw***@gmail.com").deletedBy("self").build());
        assertEquals("SELF", item.getDeletedByRole());
        assertNull(item.getDeletedBy());
        assertEquals("REMOVED", item.getState());
    }
}
