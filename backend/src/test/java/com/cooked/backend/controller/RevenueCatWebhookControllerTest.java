package com.cooked.backend.controller;

import com.cooked.backend.entity.Role;
import com.cooked.backend.entity.Status;
import com.cooked.backend.entity.SubscriptionStatus;
import com.cooked.backend.entity.SubscriptionType;
import com.cooked.backend.entity.User;
import com.cooked.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RevenueCatWebhookControllerTest {

    private UserRepository userRepository;
    private RevenueCatWebhookController controller;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        controller = new RevenueCatWebhookController(userRepository);
    }

    @Test
    void testInitialPurchaseEvent_ActivatesUserSubscription() {
        User user = User.builder()
                .email("testuser@cookedapp.com")
                .password("password")
                .role(Role.CLIENT)
                .status(Status.ACTIVE)
                .subscriptionStatus(SubscriptionStatus.FREE)
                .build();

        when(userRepository.findByEmail("testuser@cookedapp.com")).thenReturn(Optional.of(user));

        Map<String, Object> payload = Map.of(
                "event", Map.of(
                        "type", "INITIAL_PURCHASE",
                        "app_user_id", "testuser@cookedapp.com",
                        "product_id", "yearly_sub",
                        "expiration_at_ms", 1750000000000L,
                        "original_transaction_id", "trans_12345"
                )
        );

        ResponseEntity<?> response = controller.handleWebhook(null, payload);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertEquals(SubscriptionStatus.ACTIVE, savedUser.getSubscriptionStatus());
        assertEquals(SubscriptionType.YEARLY, savedUser.getSubscriptionType());
        assertNotNull(savedUser.getSubscriptionExpiresAt());
        assertEquals("trans_12345", savedUser.getOriginalTransactionId());
    }

    @Test
    void testExpirationEvent_ExpiresUserSubscription() {
        User user = User.builder()
                .email("premiumuser@cookedapp.com")
                .password("password")
                .role(Role.CLIENT)
                .status(Status.ACTIVE)
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .build();

        when(userRepository.findByEmail("premiumuser@cookedapp.com")).thenReturn(Optional.of(user));

        Map<String, Object> payload = Map.of(
                "event", Map.of(
                        "type", "EXPIRATION",
                        "app_user_id", "premiumuser@cookedapp.com"
                )
        );

        ResponseEntity<?> response = controller.handleWebhook(null, payload);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertEquals(SubscriptionStatus.EXPIRED, savedUser.getSubscriptionStatus());
    }

    @Test
    void testSecretAuthentication_RejectsInvalidHeader() {
        ReflectionTestUtils.setField(controller, "webhookSecret", "my_secret_token");

        Map<String, Object> payload = Map.of("event", Map.of("type", "INITIAL_PURCHASE", "app_user_id", "test@cookedapp.com"));

        // Test missing header
        ResponseEntity<?> responseMissing = controller.handleWebhook(null, payload);
        assertEquals(HttpStatus.UNAUTHORIZED, responseMissing.getStatusCode());

        // Test invalid token
        ResponseEntity<?> responseInvalid = controller.handleWebhook("Bearer wrong_token", payload);
        assertEquals(HttpStatus.UNAUTHORIZED, responseInvalid.getStatusCode());

        // Test valid token
        User user = User.builder().email("test@cookedapp.com").role(Role.CLIENT).status(Status.ACTIVE).build();
        when(userRepository.findByEmail("test@cookedapp.com")).thenReturn(Optional.of(user));

        ResponseEntity<?> responseValid = controller.handleWebhook("Bearer my_secret_token", payload);
        assertEquals(HttpStatus.OK, responseValid.getStatusCode());
    }
}
