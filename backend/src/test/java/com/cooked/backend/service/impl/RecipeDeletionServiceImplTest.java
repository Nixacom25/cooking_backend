package com.cooked.backend.service.impl;

import com.cooked.backend.entity.*;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.repository.CookbookRepository;
import com.cooked.backend.repository.RecipeRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.ArchiveService;
import com.cooked.backend.service.NotificationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecipeDeletionServiceImplTest {

    @Mock private RecipeRepository recipes;
    @Mock private UserRepository users;
    @Mock private CookbookRepository cookbooks;
    @Mock private NotificationService notifications;
    @Mock private ArchiveService archive;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private EntityManager em;
    @InjectMocks private RecipeDeletionServiceImpl service;

    private final User client = User.builder().id(UUID.randomUUID()).email("awa@mail.com").firstname("Awa").role(Role.CLIENT).build();
    private final User creator = User.builder().id(UUID.randomUUID()).email("chef@mail.com").firstname("Chef").role(Role.CREATOR).build();
    private final User admin = User.builder().id(UUID.randomUUID()).email("boss@cooked.app").role(Role.ADMIN).build();

    private Recipe recipe(RecipeOrigin origin, User owner) {
        Recipe r = Recipe.builder().id(UUID.randomUUID()).name("Thieb").origin(origin).user(owner)
                .isPublic(true).status(true).lastModifiedBy("awa@mail.com").cookbooks(new HashSet<>()).build();
        when(recipes.findById(r.getId())).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    void ownerDeletingAScanTurnsItIntoAnExploreRecipeToRework() {
        Recipe r = recipe(RecipeOrigin.SCAN, client);
        when(users.findAllByRole(Role.ADMIN)).thenReturn(List.of(admin));

        service.deleteByOwner(r.getId(), "awa@mail.com");

        assertEquals(RecipeOrigin.EXPLORE, r.getOrigin());
        assertEquals(RecipeOrigin.SCAN, r.getPreviousOrigin());
        assertNull(r.getUser(), "leaves the owner's library");
        assertFalse(r.isPublic());
        assertFalse(r.getStatus());
        assertFalse(r.isDeleted(), "stays assignable to interns");
        assertNull(r.getLastModifiedBy(), "back in the unmodified pool");
        assertNotNull(r.getOwnerDeletedAt());
        assertEquals("CLIENT", r.getOwnerDeletedRole());
        verify(notifications).createAndSendNotification(eq(admin), eq(client), contains("un client"), anyString(),
                eq("RECIPE_DELETED_BY_OWNER"), eq(r.getId()), isNull());
    }

    @Test
    void creatorDeletionIsLabelledCreator() {
        Recipe r = recipe(RecipeOrigin.IMPORT, creator);
        when(users.findAllByRole(Role.ADMIN)).thenReturn(List.of());

        service.deleteByOwner(r.getId(), "chef@mail.com");

        assertEquals("CREATOR", r.getOwnerDeletedRole());
        assertEquals(RecipeOrigin.IMPORT, r.getPreviousOrigin());
    }

    @Test
    void onlyTheOwnerCanDeleteTheirRecipe() {
        Recipe r = recipe(RecipeOrigin.SUGGESTED, client);
        assertThrows(BadRequestException.class, () -> service.deleteByOwner(r.getId(), "someone@mail.com"));
        assertEquals(RecipeOrigin.SUGGESTED, r.getOrigin());
    }

    @Test
    void backofficeCannotDeleteANonExploreRecipe() {
        for (RecipeOrigin o : List.of(RecipeOrigin.SCAN, RecipeOrigin.IMPORT, RecipeOrigin.SUGGESTED, RecipeOrigin.MANUAL)) {
            Recipe r = recipe(o, client);
            assertThrows(BadRequestException.class, () -> service.deleteFromBackoffice(r.getId(), "intern@cooked.app"));
            assertFalse(r.isDeleted());
        }
        verify(recipes, never()).save(any());
    }

    @Test
    void nobodyButTheOwnerDeletesAClientRecipeEvenExplore() {
        Recipe saved = recipe(RecipeOrigin.EXPLORE, client);
        assertThrows(BadRequestException.class, () -> service.deleteFromBackoffice(saved.getId(), "boss@cooked.app"));
        assertThrows(BadRequestException.class, () -> service.purge(saved.getId(), "boss@cooked.app"));
        assertFalse(saved.isDeleted());
    }

    @Test
    void keptOrphanBecomesAnExploreRecipeToRework() {
        Recipe r = recipe(RecipeOrigin.SCAN, null);
        r.setDeleted(true);
        r.setOrphanedAt(LocalDateTime.now());
        r.setPreviousOrigin(RecipeOrigin.SCAN);

        service.keepOrphan(r.getId(), "boss@cooked.app");

        assertEquals(RecipeOrigin.EXPLORE, r.getOrigin());
        assertFalse(r.isDeleted());
        assertNull(r.getOrphanedAt());
        assertNotNull(r.getOwnerDeletedAt());
        assertNull(r.getLastModifiedBy());
        assertFalse(r.isPublic());
    }

    @Test
    void backofficeDeletesExploreRecipesToTheTrash() {
        Recipe r = recipe(RecipeOrigin.EXPLORE, null);

        service.deleteFromBackoffice(r.getId(), "intern@cooked.app");

        assertTrue(r.isDeleted());
        assertEquals("intern@cooked.app", r.getDeletedBy());
        assertNotNull(r.getDeletedAt());
    }

    @Test
    void purgeOnlyWorksOnArchivedRecipes() {
        Recipe live = recipe(RecipeOrigin.EXPLORE, null);
        assertThrows(BadRequestException.class, () -> service.purge(live.getId(), "boss@cooked.app"));
        verify(recipes, never()).delete(any(Recipe.class));

        Recipe archived = recipe(RecipeOrigin.EXPLORE, null);
        archived.setOwnerDeletedAt(LocalDateTime.now());
        archived.setPreviousOrigin(RecipeOrigin.SCAN);
        Query q = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(q);
        when(q.setParameter(anyString(), any())).thenReturn(q);

        service.purge(archived.getId(), "boss@cooked.app");

        verify(archive).record(eq("RECIPE"), eq(archived.getId()), eq("Thieb"), contains("SCAN"));
        verify(recipes).delete(archived);
    }
}
