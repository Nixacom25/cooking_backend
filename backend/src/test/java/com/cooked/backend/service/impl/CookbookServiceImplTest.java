package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.CreateCookbookRequest;
import com.cooked.backend.dto.response.CookbookResponse;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.entity.Cookbook;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.CookbookRepository;
import com.cooked.backend.repository.RecipeRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.ActivityLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CookbookServiceImplTest {

    @Mock
    private CookbookRepository cookbookRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private ActivityLogService activityLogService;

    @InjectMocks
    private CookbookServiceImpl cookbookService;

    private User dummyUser;
    private Cookbook dummyCookbook;
    private UUID dummyCookbookId;

    @BeforeEach
    void setUp() {
        dummyUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .build();
        
        dummyCookbookId = UUID.randomUUID();
        dummyCookbook = Cookbook.builder()
                .id(dummyCookbookId)
                .name("My Favorites")
                .user(dummyUser)
                .build();
    }

    @Test
    void testCreateCookbook_Success() {
        CreateCookbookRequest request = new CreateCookbookRequest();
        request.setName("Desserts");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(dummyUser));
        when(cookbookRepository.existsByUserIdAndName(dummyUser.getId(), "Desserts")).thenReturn(false);
        when(cookbookRepository.save(any(Cookbook.class))).thenAnswer(i -> {
            Cookbook cb = i.getArgument(0);
            cb.setId(UUID.randomUUID());
            return cb;
        });

        CookbookResponse response = cookbookService.create("test@example.com", request);

        assertNotNull(response);
        assertEquals("Desserts", response.getName());
        verify(activityLogService).logActivity(eq(dummyUser), anyString(), anyString());
    }

    @Test
    void testCreateCookbook_AlreadyExists_ThrowsException() {
        CreateCookbookRequest request = new CreateCookbookRequest();
        request.setName("My Favorites");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(dummyUser));
        when(cookbookRepository.existsByUserIdAndName(dummyUser.getId(), "My Favorites")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> cookbookService.create("test@example.com", request));
    }

    @Test
    void testGetCookbook_Success() {
        when(cookbookRepository.findById(dummyCookbookId)).thenReturn(Optional.of(dummyCookbook));

        CookbookResponse response = cookbookService.getCookbook(dummyCookbookId, "test@example.com");

        assertNotNull(response);
        assertEquals("My Favorites", response.getName());
    }

    @Test
    void testGetCookbook_WrongUser_ThrowsException() {
        User otherUser = User.builder().email("other@example.com").build();
        dummyCookbook.setUser(otherUser);
        
        when(cookbookRepository.findById(dummyCookbookId)).thenReturn(Optional.of(dummyCookbook));

        assertThrows(BadRequestException.class, () -> cookbookService.getCookbook(dummyCookbookId, "test@example.com"));
    }

    @Test
    void testDeleteCookbook_Success() {
        when(cookbookRepository.findById(dummyCookbookId)).thenReturn(Optional.of(dummyCookbook));

        MessageResponse response = cookbookService.delete(dummyCookbookId, "test@example.com");

        assertNotNull(response);
        verify(cookbookRepository).delete(dummyCookbook);
    }
}
