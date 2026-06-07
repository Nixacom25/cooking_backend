package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.UpdateUserRequest;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.dto.response.UserResponse;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.repository.RecipeRepository;
import com.cooked.backend.repository.MealPlanRepository;
import com.cooked.backend.repository.GroceryItemRepository;
import com.cooked.backend.service.CloudinaryService;
import com.cooked.backend.service.EmailService;
import com.cooked.backend.service.UserInitializationService;
import jakarta.persistence.EntityManager;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private CloudinaryService cloudinaryService;
    @Mock
    private EmailService emailService;
    @Mock
    private UserInitializationService userInitializationService;
    @Mock
    private RecipeRepository recipeRepository;
    @Mock
    private MealPlanRepository mealPlanRepository;
    @Mock
    private GroceryItemRepository groceryItemRepository;
    @Mock
    private EntityManager entityManager;
    
    @Mock
    private com.cooked.backend.mapper.UserMapper userMapper;

    @InjectMocks
    private UserServiceImpl userService;

    private User dummyUser;

    @BeforeEach
    void setUp() {
        dummyUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .firstname("John")
                .lastname("Doe")
                .build();
    }

    @Test
    void testGetProfile_Success() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(dummyUser));
        UserResponse ur = new UserResponse();
        ur.setFirstname("John");
        ur.setLastname("Doe");
        ur.setEmail("test@example.com");
        when(userMapper.toResponse(dummyUser)).thenReturn(ur);

        UserResponse response = userService.getCurrentUser("test@example.com");

        assertNotNull(response);
        assertEquals("John", response.getFirstname());
        assertEquals("Doe", response.getLastname());
        assertEquals("test@example.com", response.getEmail());
    }

    @Test
    void testGetProfile_NotFound() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getCurrentUser("unknown@example.com"));
    }

    @Test
    void testUpdateProfile_Success() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setFirstname("Jane");
        request.setLastname("Smith");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(dummyUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        
        UserResponse ur = new UserResponse();
        ur.setFirstname("Jane");
        ur.setLastname("Smith");
        when(userMapper.toResponse(dummyUser)).thenReturn(ur);

        UserResponse response = userService.updateCurrentUser("test@example.com", request);

        assertNotNull(response);
        assertEquals("Jane", response.getFirstname());
        assertEquals("Smith", response.getLastname());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void testDeleteAccount_Success() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(dummyUser));
        
        MessageResponse response = userService.deleteCurrentUser("test@example.com");

        assertNotNull(response);
        verify(userRepository).delete(dummyUser);
    }
}
