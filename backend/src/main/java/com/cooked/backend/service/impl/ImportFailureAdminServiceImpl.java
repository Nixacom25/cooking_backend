package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.ImportRetryResponse;
import com.cooked.backend.dto.response.RecipeResponse;
import com.cooked.backend.entity.ProductEvent;
import com.cooked.backend.entity.ProductEventType;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.ProductEventRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.ImportFailureAdminService;
import com.cooked.backend.service.ProductEventTracker;
import com.cooked.backend.service.RecipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/** No transaction around the retry: the AI import can take a while and must not hold a connection. */
@Service
@RequiredArgsConstructor
public class ImportFailureAdminServiceImpl implements ImportFailureAdminService {

    private final ProductEventRepository events;
    private final UserRepository users;
    private final RecipeService recipeService;
    private final ProductEventTracker tracker;

    @Override
    public void resolve(UUID eventId) {
        ProductEvent e = failedImport(eventId);
        if (e.getResolvedAt() == null) {
            e.setResolvedAt(LocalDateTime.now());
            events.save(e);
        }
    }

    @Override
    public ImportRetryResponse retry(UUID eventId) {
        ProductEvent e = failedImport(eventId);
        if (e.getTarget() == null || e.getTarget().isBlank()) {
            throw new BadRequestException("This failure was recorded before URLs were kept: it cannot be retried from here.");
        }
        User user = e.getUserId() == null ? null : users.findById(e.getUserId()).orElse(null);
        if (user == null) throw new BadRequestException("The user of this import no longer exists.");

        ImportRetryResponse result;
        try {
            RecipeResponse recipe = tracker.track(ProductEventType.IMPORT, user.getEmail(), e.getDetail(), e.getTarget(),
                    () -> recipeService.importAndSaveAsSuggestion(e.getTarget(), null, user.getEmail()), r -> 1);
            result = new ImportRetryResponse(true, "Imported: the recipe is in the user's suggestions.", recipe == null ? null : recipe.getId());
        } catch (RuntimeException ex) {
            result = new ImportRetryResponse(false, ProductEventTrackerImpl.reason(ex), null);
        }
        e.setRetriedAt(LocalDateTime.now());
        e.setRetrySucceeded(result.success());
        if (result.success()) e.setResolvedAt(LocalDateTime.now());
        events.save(e);
        return result;
    }

    private ProductEvent failedImport(UUID id) {
        ProductEvent e = events.findById(id).orElseThrow(() -> new ResourceNotFoundException("Import failure not found"));
        if (e.getType() != ProductEventType.IMPORT || e.isSuccess()) throw new BadRequestException("This event is not a failed import.");
        return e;
    }
}
