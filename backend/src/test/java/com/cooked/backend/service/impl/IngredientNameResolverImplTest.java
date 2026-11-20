package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.IngredientPayload;
import com.cooked.backend.repository.IngredientVisualRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IngredientNameResolverImplTest {

    private IngredientNameResolverImpl resolver;

    private static Object[] row(String name, String key) {
        return new Object[]{name, key, key};
    }

    @BeforeEach
    void setUp() {
        IngredientVisualRepository visuals = mock(IngredientVisualRepository.class);
        List<Object[]> names = new ArrayList<>(List.of(
                row("Tomato", "tomato"), row("Roma tomato", "roma_tomato"), row("Scallion", "scallion"),
                row("Garlic", "garlic"), row("Cloves", "clove"), row("Rice", "rice"), row("Cilantro", "cilantro"),
                row("Greens", "green"), row("Sauce", "sauce"),
                row("Tbsp poudre de curry", "tbsp_poudre_de_curry"), row("Ingredients:", "ingredient")));
        when(visuals.findEnabledNames()).thenReturn(names);
        List<Object[]> aliases = new ArrayList<>();
        aliases.add(new Object[]{"green_onion", "Scallion"});
        when(visuals.findEnabledAliases()).thenReturn(aliases);
        resolver = new IngredientNameResolverImpl(visuals);
    }

    @Test
    void exactNamesAndAliasesResolveToTheCatalogName() {
        assertEquals(Optional.of("Tomato"), resolver.resolve("Tomatoes"));
        assertEquals(Optional.of("Scallion"), resolver.resolve("Green Onions"));
    }

    @Test
    void longerNamesResolveToTheIngredientTheyContain() {
        assertEquals(Optional.of("Roma tomato"), resolver.resolve("ripe roma tomatoes"));
        assertEquals(Optional.of("Garlic"), resolver.resolve("garlic cloves"));
        assertEquals(Optional.of("Rice"), resolver.resolve("cilantro lime rice"));
        assertEquals(Optional.of("Tomato"), resolver.resolve("Tomato (or red pepper, optional)"));
    }

    @Test
    void unknownOrMisleadingNamesAreNotInvented() {
        assertTrue(resolver.resolve("unicorn steak").isEmpty());
        assertTrue(resolver.resolve("green chile").isEmpty(), "\"green\" is an adjective here, not Greens");
        assertTrue(resolver.resolve("mystery ingredient").isEmpty());
        assertTrue(resolver.resolve("curry").isEmpty(), "a recipe line is not a catalog name to match inside");
        assertTrue(resolver.resolve(" ").isEmpty());
    }

    @Test
    void keepKnownDropsUnknownNamesAndDuplicates() {
        List<IngredientPayload> out = resolver.keepKnown(List.of(
                IngredientPayload.builder().name("tomatoes").icon("🍅").build(),
                IngredientPayload.builder().name("Tomato").build(),
                IngredientPayload.builder().name("dragon egg").build(),
                IngredientPayload.builder().name("green onion").build()));
        assertEquals(List.of("Tomato", "Scallion"), out.stream().map(IngredientPayload::getName).toList());
        assertEquals("🍅", out.get(0).getIcon());
        assertNull(resolver.keepKnown(null));
    }
}
