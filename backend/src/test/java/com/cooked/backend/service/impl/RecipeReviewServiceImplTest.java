package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.ReviewChangesResponse;
import com.cooked.backend.entity.*;
import com.cooked.backend.repository.RecipeAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecipeReviewServiceImplTest {

    @Mock private RecipeAssignmentRepository assignments;
    @InjectMocks private RecipeReviewServiceImpl service;

    private Recipe recipe() {
        Recipe r = Recipe.builder().id(UUID.randomUUID()).name("Thieb").origin(RecipeOrigin.EXPLORE)
                .steps(new ArrayList<>(List.of("Wash the rice", "Cook the fish"))).servings(4).prepTime(20).cookTime(60)
                .recipeIngredients(new HashSet<>()).categories(new HashSet<>()).equipment(new ArrayList<>()).build();
        r.getRecipeIngredients().add(RecipeIngredient.builder().recipe(r).quantity("500 g")
                .ingredient(Ingredient.builder().name("Rice").build()).build());
        return r;
    }

    private Map<String, Boolean> changed(ReviewChangesResponse res) {
        Map<String, Boolean> m = new HashMap<>();
        res.getFields().forEach(f -> m.put(f.getKey(), f.isChanged()));
        return m;
    }

    @Test
    void onlyTheEditedPartsAreFlagged() {
        Recipe r = recipe();
        RecipeAssignment a = RecipeAssignment.builder().id(UUID.randomUUID()).recipe(r).status(AssignmentStatus.ASSIGNED).build();
        service.captureBaseline(a);

        r.setName("Thiéboudienne");
        r.getSteps().add("Serve");
        when(assignments.findById(a.getId())).thenReturn(Optional.of(a));

        ReviewChangesResponse res = service.changes(a.getId());
        Map<String, Boolean> c = changed(res);
        assertTrue(res.isBaselineAvailable());
        assertFalse(res.isBaselineLate());
        assertTrue(c.get("name"));
        assertTrue(c.get("steps"));
        assertFalse(c.get("ingredients"));
        assertFalse(c.get("servings"));
        assertFalse(c.get("image"));
        assertEquals(RecipeReviewServiceImpl.FIELDS.size(), res.getFields().size());
    }

    @Test
    void sendBackInstructionsAreCheckedAtTheNextReview() {
        Recipe r = recipe();
        RecipeAssignment a = RecipeAssignment.builder().id(UUID.randomUUID()).recipe(r).status(AssignmentStatus.SUBMITTED_FOR_VALIDATION).build();
        service.captureBaseline(a);
        service.recordSendBack(a, List.of("steps", "servings", "bogus"), List.of("name"));
        assertEquals(List.of("steps", "servings"), service.fieldsToFix(a));

        r.getSteps().set(0, "Rinse the rice three times"); // steps fixed, servings forgotten
        when(assignments.findById(a.getId())).thenReturn(Optional.of(a));

        ReviewChangesResponse res = service.changes(a.getId());
        Map<String, Boolean> done = new HashMap<>();
        res.getInstructions().forEach(i -> done.put(i.getKey(), i.isRespected()));
        assertTrue(done.get("steps"));
        assertFalse(done.get("servings"));
        assertEquals(List.of("name"), res.getPreviouslyApproved());
    }

    @Test
    void oldAssignmentsGetALateSnapshotAtTheFirstSave() {
        Recipe r = recipe();
        RecipeAssignment old = RecipeAssignment.builder().id(UUID.randomUUID()).recipe(r).status(AssignmentStatus.IN_PROGRESS).build();
        RecipeAssignment review = RecipeAssignment.builder().id(UUID.randomUUID()).recipe(r).status(AssignmentStatus.SUBMITTED_FOR_VALIDATION).build();
        when(assignments.findActiveAssignmentsByRecipeId(r.getId())).thenReturn(List.of(old, review));

        service.captureBaselineIfMissing(r);

        assertNotNull(old.getBaselineSnapshot());
        assertTrue(old.getBaselineLate());
        assertNull(review.getBaselineSnapshot(), "a submitted recipe is not re-snapshotted");
        verify(assignments, times(1)).save(any());
    }

    @Test
    void withoutSnapshotNothingIsFlagged() {
        RecipeAssignment a = RecipeAssignment.builder().id(UUID.randomUUID()).recipe(recipe()).status(AssignmentStatus.SUBMITTED_FOR_VALIDATION).build();
        when(assignments.findById(a.getId())).thenReturn(Optional.of(a));
        ReviewChangesResponse res = service.changes(a.getId());
        assertFalse(res.isBaselineAvailable());
        assertTrue(res.getFields().stream().noneMatch(ReviewChangesResponse.FieldChange::isChanged));
    }
}
