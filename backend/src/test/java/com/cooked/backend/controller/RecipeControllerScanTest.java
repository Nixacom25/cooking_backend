package com.cooked.backend.controller;

import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.cooked.backend.dto.response.ScanResponse;
import com.cooked.backend.entity.ProductEventType;
import com.cooked.backend.service.AiService;
import com.cooked.backend.service.CreatorRecipeImageService;
import com.cooked.backend.service.IngredientNameReporter;
import com.cooked.backend.service.ProductEventTracker;
import com.cooked.backend.service.RecipeService;
import com.cooked.backend.service.TrendingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeControllerScanTest {

    private static final String CREATOR_EMAIL = "creator@example.com";

    @Mock private RecipeService recipeService;
    @Mock private AiService aiService;
    @Mock private TrendingService trendingService;
    @Mock private ProductEventTracker eventTracker;
    @Mock private IngredientNameReporter ingredientNames;
    @Mock private CreatorRecipeImageService creatorRecipeImages;
    @InjectMocks private RecipeController controller;

    @Test
    void imageScanStillReturnsRecipesWhenCreatorPhotoAttachmentFails() {
        ScanResponse scan = successfulScan();
        Authentication auth = authentication();
        MockMultipartFile file = new MockMultipartFile("file", "ingredients.jpg", "image/jpeg", new byte[] {1});
        stubTrackedResult("photo");
        when(aiService.scan(file, CREATOR_EMAIL)).thenReturn(scan);
        doThrow(new IllegalStateException("photo storage unavailable"))
                .when(creatorRecipeImages).attachImages(scan.getRecipes(), CREATOR_EMAIL);

        var response = controller.scanRecipeViaImage(file, auth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(scan, response.getBody());
        verify(creatorRecipeImages).attachImages(scan.getRecipes(), CREATOR_EMAIL);
    }

    @Test
    void typedScanStillReturnsRecipesWhenCreatorPhotoAttachmentFails() {
        ScanResponse scan = successfulScan();
        Authentication auth = authentication();
        List<String> ingredients = List.of("tomato");
        stubTrackedResult("typed");
        when(aiService.scanTyped(ingredients, CREATOR_EMAIL)).thenReturn(scan);
        doThrow(new IllegalStateException("photo storage unavailable"))
                .when(creatorRecipeImages).attachImages(scan.getRecipes(), CREATOR_EMAIL);

        var response = controller.scanTypedIngredients(Map.of("ingredients", ingredients), auth);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(scan, response.getBody());
        verify(creatorRecipeImages).attachImages(scan.getRecipes(), CREATOR_EMAIL);
    }

    private void stubTrackedResult(String detail) {
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get())
                .when(eventTracker).track(
                        eq(ProductEventType.SCAN),
                        eq(CREATOR_EMAIL),
                        eq(detail),
                        any(Supplier.class),
                        any(ToIntFunction.class));
    }

    private Authentication authentication() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(CREATOR_EMAIL);
        return auth;
    }

    private ScanResponse successfulScan() {
        return ScanResponse.builder()
                .success(true)
                .recipes(List.of(new CreateRecipeRequest()))
                .build();
    }
}
