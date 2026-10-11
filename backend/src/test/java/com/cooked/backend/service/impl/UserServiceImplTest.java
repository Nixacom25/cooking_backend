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
import static org.mockito.ArgumentMatchers.eq;
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
    private com.cooked.backend.repository.CookbookRepository cookbookRepository;
    @Mock
    private com.cooked.backend.repository.DeviceSessionRepository deviceSessionRepository;
    @Mock
    private com.cooked.backend.repository.RecipeAssignmentRepository recipeAssignmentRepository;
    @Mock
    private EntityManager entityManager;
    
    @Mock
    private com.cooked.backend.mapper.UserMapper userMapper;

    @Mock
    private com.cooked.backend.service.SubscriptionVerificationService subscriptionVerificationService;

    @Mock
    private com.cooked.backend.service.RevenueCatReconciliationService revenueCatReconciliation;

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
                .role(com.cooked.backend.entity.Role.ADMIN)
                .subscriptionStatus(com.cooked.backend.entity.SubscriptionStatus.ACTIVE)
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

    private java.util.Map<String, Object> appSaysYearlyActive() {
        return java.util.Map.of("isActive", true, "expirationDate", "2027-10-02T05:23:44Z", "productId", "yearly_sub");
    }

    @Test
    void syncFallsBackToAppDataWhenRevenueCatUnavailable() {
        dummyUser.setSubscriptionStatus(com.cooked.backend.entity.SubscriptionStatus.EXPIRED);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(dummyUser));
        when(subscriptionVerificationService.isAvailable()).thenReturn(true);
        when(subscriptionVerificationService.verify(eq(dummyUser), any()))
                .thenReturn(com.cooked.backend.service.SubscriptionVerificationService.Result.UNAVAILABLE);

        userService.syncSubscription("test@example.com", appSaysYearlyActive());

        assertEquals(com.cooked.backend.entity.SubscriptionStatus.ACTIVE, dummyUser.getSubscriptionStatus());
        assertEquals(java.time.LocalDateTime.ofInstant(java.time.Instant.parse("2027-10-02T05:23:44Z"),
                java.time.ZoneId.systemDefault()), dummyUser.getSubscriptionExpiresAt());
    }

    @Test
    void syncIgnoresAppClaimWhenRevenueCatSaysNotEntitled() {
        dummyUser.setSubscriptionStatus(com.cooked.backend.entity.SubscriptionStatus.EXPIRED);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(dummyUser));
        when(subscriptionVerificationService.isAvailable()).thenReturn(true);
        when(subscriptionVerificationService.verify(eq(dummyUser), any()))
                .thenReturn(com.cooked.backend.service.SubscriptionVerificationService.Result.NOT_ENTITLED);

        userService.syncSubscription("test@example.com", appSaysYearlyActive());

        assertEquals(com.cooked.backend.entity.SubscriptionStatus.EXPIRED, dummyUser.getSubscriptionStatus());
    }
}
