package com.cooked.backend.util;

import com.cooked.backend.dto.request.InstacartIngredientDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class IngredientNormalizerTest {

    @Test
    public void testNormalize_withQuantityAndUnit() {
        InstacartIngredientDto result = IngredientNormalizer.normalize("Chicken breast", "2 pieces");
        assertNotNull(result);
        assertEquals("chicken breast", result.getName());
        assertEquals(2.0, result.getQuantity());
        assertEquals("pieces", result.getUnit());
    }

    @Test
    public void testNormalize_withLeadingQuantityInName() {
        InstacartIngredientDto result = IngredientNormalizer.normalize("2 large tomatoes, chopped", "");
        assertNotNull(result);
        assertEquals("tomatoes", result.getName());
        assertEquals(2.0, result.getQuantity());
        assertEquals("pieces", result.getUnit());
        assertTrue(result.getNotes().contains("large"));
        assertTrue(result.getNotes().contains("chopped"));
    }

    @Test
    public void testNormalize_withGramsAndOliveOil() {
        InstacartIngredientDto result = IngredientNormalizer.normalize("Olive oil", "2 tablespoons");
        assertNotNull(result);
        assertEquals("olive oil", result.getName());
        assertEquals(2.0, result.getQuantity());
        assertEquals("tablespoons", result.getUnit());
    }

    @Test
    public void testNormalize_withNoQuantity() {
        InstacartIngredientDto result = IngredientNormalizer.normalize("Salt", null);
        assertNotNull(result);
        assertEquals("salt", result.getName());
        assertNull(result.getQuantity());
        assertNull(result.getUnit());
    }
}
