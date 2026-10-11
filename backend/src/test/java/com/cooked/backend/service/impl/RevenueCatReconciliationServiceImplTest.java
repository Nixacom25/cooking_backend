package com.cooked.backend.service.impl;

import com.cooked.backend.entity.RevenueCatPendingEvent;
import com.cooked.backend.entity.SubscriptionPayment;
import com.cooked.backend.entity.User;
import com.cooked.backend.repository.RevenueCatPendingEventRepository;
import com.cooked.backend.repository.SubscriptionPaymentRepository;
import com.cooked.backend.repository.SubscriptionPlanRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.ActivityLogService;
import com.cooked.backend.service.RevenueCatApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RevenueCatReconciliationServiceImplTest {

    private RevenueCatPendingEventRepository pending;
    private SubscriptionPaymentRepository payments;
    private RevenueCatApiClient revenueCat;
    private RevenueCatReconciliationServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        pending = mock(RevenueCatPendingEventRepository.class);
        payments = mock(SubscriptionPaymentRepository.class);
        revenueCat = mock(RevenueCatApiClient.class);
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new RevenueCatReconciliationServiceImpl(pending, payments, mock(SubscriptionPlanRepository.class), mock(UserRepository.class),
                revenueCat, mock(ActivityLogService.class), null, tm);
        user = com.cooked.backend.entity.User.builder().id(UUID.randomUUID()).email("emely@x.com").build();
        user.setRevenueCatCustomerId("$RCAnonymousID:348");
        when(payments.save(any())).thenAnswer(i -> { SubscriptionPayment p = i.getArgument(0); p.setId(UUID.randomUUID()); return p; });
    }

    @Test
    void anOnboardingPurchaseIsRecordedOnceTheAppReportsItsId() throws Exception {
        Map<String, Object> event = Map.of("id", "evt1", "type", "RENEWAL", "app_user_id", "$RCAnonymousID:348", "price", 29.99,
                "product_id", "yearly_sub", "store", "APP_STORE", "environment", "PRODUCTION", "purchased_at_ms", 1790918600000L);
        RevenueCatPendingEvent e = RevenueCatPendingEvent.builder().eventId("evt1").eventType("RENEWAL")
                .appUserIds(",$RCAnonymousID:348,").payload(new ObjectMapper().writeValueAsString(event)).build();
        when(pending.findOpenFor("$RCAnonymousID:348")).thenReturn(List.of(e));

        assertEquals(1, service.resolveFor(user));

        ArgumentCaptor<SubscriptionPayment> saved = ArgumentCaptor.forClass(SubscriptionPayment.class);
        verify(payments).save(saved.capture());
        assertEquals("SUCCESS", saved.getValue().getStatus());
        assertEquals(0, new BigDecimal("29.99").compareTo(saved.getValue().getAmount()));
        assertEquals("Apple", saved.getValue().getStore());
        assertEquals("rc_evt1", saved.getValue().getStripePaymentId());
        verify(payments).setCreatedAt(any(), notNull());
        assertNotNull(e.getResolvedAt());
    }

    @Test
    void syncAddsRealPurchasesOnlyOnce() throws Exception {
        var sub = new ObjectMapper().readTree("""
                {"subscriptions":{
                  "yearly_sub":{"purchase_date":"2026-10-02T05:23:44Z","period_type":"normal","is_sandbox":false,"store":"app_store",
                                "store_transaction_id":"200001","price":{"amount":29.99,"currency":"USD"}},
                  "monthly_sub":{"purchase_date":"2026-10-08T00:00:00Z","period_type":"trial","is_sandbox":false,"store":"app_store"},
                  "test_sub":{"purchase_date":"2026-10-09T00:00:00Z","period_type":"normal","is_sandbox":true,"store":"app_store"}}}""");
        when(revenueCat.fetchSubscriber(user.getId().toString())).thenReturn(sub);

        assertEquals(1, service.reconcile(user));
        verify(payments).save(argThat(p -> "rc_sync_200001".equals(p.getStripePaymentId()) && "YEARLY".equals(p.getPlanType())));

        when(payments.existsRealPaymentBetween(eq(user.getId()), any(), any())).thenReturn(true);
        assertEquals(0, service.reconcile(user)); // already recorded (webhook or earlier sync)
    }
}
