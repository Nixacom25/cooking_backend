package com.cooked.backend.service.impl;

import com.cooked.backend.entity.CategoryType;
import com.cooked.backend.entity.RecipeCategory;
import com.cooked.backend.repository.RecipeCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TaxonomyServiceImplTest {

    @Mock
    private RecipeCategoryRepository categoryRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private TaxonomyServiceImpl taxonomyService;

    private RecipeCategory dummyCategory;
    private UUID dummyCategoryId;

    @BeforeEach
    void setUp() {
        dummyCategoryId = UUID.randomUUID();
        dummyCategory = RecipeCategory.builder()
                .id(dummyCategoryId)
                .name("Italian")
                .type(CategoryType.CUISINE)
                .image("image.jpg")
                .build();
    }

    @Test
    void testGetOrCreateCategory_Existing() {
        when(categoryRepository.findByNameAndType("Italian", CategoryType.CUISINE)).thenReturn(Optional.of(dummyCategory));
        when(categoryRepository.save(any(RecipeCategory.class))).thenAnswer(i -> i.getArgument(0));
        
        RecipeCategory response = taxonomyService.getOrCreateCategory("Italian", CategoryType.CUISINE);

        assertNotNull(response);
        assertEquals("Italian", response.getName());
    }

    @Test
    void testGetOrCreateCategory_New() {
        when(categoryRepository.findByNameAndType("Mexican", CategoryType.CUISINE)).thenReturn(Optional.empty());
        when(categoryRepository.save(any(RecipeCategory.class))).thenAnswer(i -> {
            RecipeCategory cat = i.getArgument(0);
            cat.setId(UUID.randomUUID());
            return cat;
        });

        RecipeCategory response = taxonomyService.getOrCreateCategory("Mexican", CategoryType.CUISINE);

        assertNotNull(response);
        assertEquals("Mexican", response.getName());
    }
}
