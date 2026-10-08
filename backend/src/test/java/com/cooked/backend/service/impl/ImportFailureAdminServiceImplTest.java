package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.RecipeResponse;
import com.cooked.backend.entity.ProductEvent;
import com.cooked.backend.entity.ProductEventType;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.repository.ProductEventRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.ProductEventTracker;
import com.cooked.backend.service.RecipeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImportFailureAdminServiceImplTest {

    @Mock private ProductEventRepository events;
    @Mock private UserRepository users;
    @Mock private RecipeService recipes;
    @Mock private ProductEventTracker tracker;
    @InjectMocks private ImportFailureAdminServiceImpl service;

    private final UUID id = UUID.randomUUID(), userId = UUID.randomUUID();

    private ProductEvent failed(String target) {
        return ProductEvent.builder().id(id).type(ProductEventType.IMPORT).success(false).detail("tiktok.com").target(target).userId(userId).build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void retryRunsTheImportForTheUserAndResolvesOnSuccess() {
        ProductEvent e = failed("https://tiktok.com/v/1");
        when(events.findById(id)).thenReturn(Optional.of(e));
        when(users.findById(userId)).thenReturn(Optional.of(User.builder().email("ana@x.com").build()));
        RecipeResponse recipe = new RecipeResponse();
        recipe.setId(UUID.randomUUID());
        when(tracker.track(eq(ProductEventType.IMPORT), eq("ana@x.com"), eq("tiktok.com"), eq("https://tiktok.com/v/1"), any(Supplier.class), any()))
                .thenReturn(recipe);

        var out = service.retry(id);

        assertTrue(out.success());
        assertEquals(recipe.getId(), out.recipeId());
        assertTrue(e.getRetrySucceeded());
        assertNotNull(e.getResolvedAt());
        verify(events).save(e);
    }

    @Test
    @SuppressWarnings("unchecked")
    void failedRetryIsRecordedAndOldFailuresCannotRetry() {
        ProductEvent e = failed("https://x.com/r");
        when(events.findById(id)).thenReturn(Optional.of(e));
        when(users.findById(userId)).thenReturn(Optional.of(User.builder().email("ana@x.com").build()));
        when(tracker.track(any(), any(), any(), any(), any(Supplier.class), any())).thenThrow(new BadRequestException("Blocked by the site"));
        var out = service.retry(id);
        assertFalse(out.success());
        assertEquals("Blocked by the site", out.message());
        assertFalse(e.getRetrySucceeded());
        assertNull(e.getResolvedAt());

        when(events.findById(id)).thenReturn(Optional.of(failed(null)));
        assertThrows(BadRequestException.class, () -> service.retry(id));
    }
}
