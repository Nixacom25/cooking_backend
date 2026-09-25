package com.cooked.backend.controller;

import com.cooked.backend.entity.Role;
import com.cooked.backend.entity.Status;
import com.cooked.backend.entity.SubscriptionStatus;
import com.cooked.backend.entity.SubscriptionType;
import com.cooked.backend.entity.User;
import com.cooked.backend.repository.SubscriptionPaymentRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.EmailService;
import com.cooked.backend.service.PushNotificationService;
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
    private EmailService emailService;
    private PushNotificationService pushNotificationService;
    private SubscriptionPaymentRepository subscriptionPaymentRepository;
    private RevenueCatWebhookController controller;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        emailService = mock(EmailService.class);
        pushNotificationService = mock(PushNotificationService.class);
        subscriptionPaymentRepository = mock(SubscriptionPaymentRepository.class);
        controller = new RevenueCatWebhookController(userRepository, emailService, pushNotificationService,
                subscriptionPaymentRepository);
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
    void testInitialPurchaseEvent_RecordsSubscriptionPayment() {
        User user = User.builder()
                .email("payer@cookedapp.com")
                .password("password")
                .role(Role.CLIENT)
                .status(Status.ACTIVE)
                .subscriptionStatus(SubscriptionStatus.FREE)
                .build();

        when(userRepository.findByEmail("payer@cookedapp.com")).thenReturn(Optional.of(user));
        when(subscriptionPaymentRepository.existsByStripePaymentId("rc_evt_1")).thenReturn(false);

        Map<String, Object> payload = Map.of(
                "event", Map.of(
                        "id", "evt_1",
                        "type", "INITIAL_PURCHASE",
                        "app_user_id", "payer@cookedapp.com",
                        "product_id", "yearly_sub",
                        "price", 59.99,
                        "currency", "USD",
                        "store", "APP_STORE",
                        "expiration_at_ms", 1750000000000L
                )
        );

        ResponseEntity<?> response = controller.handleWebhook(null, payload);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        ArgumentCaptor<com.cooked.backend.entity.SubscriptionPayment> paymentCaptor =
                ArgumentCaptor.forClass(com.cooked.backend.entity.SubscriptionPayment.class);
        verify(subscriptionPaymentRepository, times(1)).save(paymentCaptor.capture());

        com.cooked.backend.entity.SubscriptionPayment saved = paymentCaptor.getValue();
        assertEquals(0, saved.getAmount().compareTo(java.math.BigDecimal.valueOf(59.99)));
        assertEquals("YEARLY", saved.getPlanType());
        assertEquals("SUCCESS", saved.getStatus());
        assertEquals("Apple", saved.getStore());
        assertEquals("rc_evt_1", saved.getStripePaymentId());
    }

    @Test
    void testInitialPurchaseEvent_SkipsDuplicatePaymentOnRetry() {
        User user = User.builder()
                .email("retry@cookedapp.com")
                .password("password")
                .role(Role.CLIENT)
                .status(Status.ACTIVE)
                .subscriptionStatus(SubscriptionStatus.FREE)
                .build();

        when(userRepository.findByEmail("retry@cookedapp.com")).thenReturn(Optional.of(user));
        when(subscriptionPaymentRepository.existsByStripePaymentId("rc_evt_2")).thenReturn(true);

        Map<String, Object> payload = Map.of(
                "event", Map.of(
                        "id", "evt_2",
                        "type", "RENEWAL",
                        "app_user_id", "retry@cookedapp.com",
                        "product_id", "monthly_sub",
                        "price", 9.99,
                        "currency", "USD",
                        "store", "PLAY_STORE"
                )
        );

        ResponseEntity<?> response = controller.handleWebhook(null, payload);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Already recorded for this event id (a RevenueCat retry) - must not double-count.
        verify(subscriptionPaymentRepository, never()).save(any());
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

    @Test
    void testBillingIssueEvent_SendsPaymentFailureEmail() {
        User user = User.builder()
                .email("billingissue@cookedapp.com")
                .firstname("Sam")
                .password("password")
                .role(Role.CLIENT)
                .status(Status.ACTIVE)
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .build();

        when(userRepository.findByEmail("billingissue@cookedapp.com")).thenReturn(Optional.of(user));

        Map<String, Object> payload = Map.of(
                "event", Map.of(
                        "type", "BILLING_ISSUE",
                        "app_user_id", "billingissue@cookedapp.com",
                        "product_id", "monthly_sub"
                )
        );

        ResponseEntity<?> response = controller.handleWebhook(null, payload);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        verify(emailService, times(1))
                .sendPaymentFailureEmail(eq("billingissue@cookedapp.com"), eq("Sam"), eq("Monthly"), anyString());
        verify(pushNotificationService, times(1))
                .sendPush(isNull(), anyString(), anyString(), anyMap());
        // A billing issue alone doesn't change entitlement - only EXPIRATION/CANCELLATION do.
        verify(userRepository, never()).save(any());
    }
}
