package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.RecipeDataRepository;
import com.cooked.backend.repository.RecipeRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.SubscriptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MarkhorAiServiceImplTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private RecipeDataRepository recipeDataRepository;

    @InjectMocks
    private MarkhorAiServiceImpl aiService;

    private User dummyUser;

    @BeforeEach
    void setUp() {
        dummyUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .build();
        ReflectionTestUtils.setField(aiService, "baseUrl", "http://dummy-ai-service");
        ReflectionTestUtils.setField(aiService, "internalSecret", "dummy-secret");
    }

    @Test
    void testSearchWeb_UserNotFound() {
        when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> aiService.searchWeb("recipe", "notfound@example.com"));
    }

    @Test
    void testGenerateTrendingDishes_Success() {
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("trending", Arrays.asList("Dish A", "Dish B"));
        
        ResponseEntity<Map<String, Object>> mockResponse = new ResponseEntity<>(responseBody, HttpStatus.OK);
        
        when(restTemplate.exchange(
                eq("http://dummy-ai-service/api/recipes/trending"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(mockResponse);

        List<String> result = aiService.generateTrendingDishes();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Dish A", result.get(0));
    }

    @Test
    void testGenerateTrendingDishes_Fallback() {
        when(restTemplate.exchange(
                anyString(),
                any(HttpMethod.class),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenThrow(new RuntimeException("API Error"));

        List<String> result = aiService.generateTrendingDishes();

        assertNotNull(result);
        assertTrue(result.contains("Chicken Tacos"));
    }
}
