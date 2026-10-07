package com.cooked.backend.service.impl;

import com.cooked.backend.entity.GiftCode;
import com.cooked.backend.entity.GiftPlan;
import com.cooked.backend.repository.GiftCodeRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.ActivityLogService;
import com.cooked.backend.service.EmailService;
import com.cooked.backend.service.RevenueCatApiClient;
import com.cooked.backend.service.StripeClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GiftWebPurchaseTest {

    private GiftCodeRepository giftRepo;
    private EmailService email;
    private GiftServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        giftRepo = mock(GiftCodeRepository.class);
        UserRepository users = mock(UserRepository.class);
        email = mock(EmailService.class);
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());
        when(giftRepo.save(any(GiftCode.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new GiftServiceImpl(giftRepo, users, email, mock(ActivityLogService.class),
                mock(RevenueCatApiClient.class), mock(io.github.bucket4j.distributed.proxy.ProxyManager.class),
                mock(StripeClient.class), mock(com.cooked.backend.service.AmbassadorService.class));
    }

    private JsonNode session(String id, String plan, long amount) throws Exception {
        return new ObjectMapper().readTree("""
            {"id":"%s","payment_status":"paid","amount_total":%d,
             "customer_details":{"email":"buyer@mail.com"},
             "metadata":{"type":"gift","plan":"%s","recipient_email":"friend@mail.com","sender_name":"Awa"}}
            """.formatted(id, amount, plan));
    }

    @Test
    void paidSessionCreatesACookCodeAndEmailsBothPeople() throws Exception {
        service.createFromWebPurchase(session("cs_1", "ONE_YEAR", 2999));

        ArgumentCaptor<GiftCode> saved = ArgumentCaptor.forClass(GiftCode.class);
        verify(giftRepo).save(saved.capture());
        assertTrue(saved.getValue().getCode().matches("COOK-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}"));
        assertEquals(GiftPlan.ONE_YEAR, saved.getValue().getPlan());
        verify(email).sendGiftPurchaseReceiptEmail(eq("buyer@mail.com"), eq("1 Year"), anyString(), eq("friend@mail.com"), anyString());
        verify(email).sendGiftReceivedEmail(eq("friend@mail.com"), eq("Awa"), eq("1 Year"), anyString(), anyString());
    }

    @Test
    void retriedWebhookDoesNotCreateASecondCode() throws Exception {
        when(giftRepo.existsByPurchaseRef("stripe_cs_2")).thenReturn(true);
        service.createFromWebPurchase(session("cs_2", "ONE_MONTH", 999));
        verify(giftRepo, never()).save(any());
        verifyNoInteractions(email);
    }

    @Test
    void wrongAmountIsRefused() throws Exception {
        service.createFromWebPurchase(session("cs_3", "ONE_YEAR", 100));
        verify(giftRepo, never()).save(any());
        verifyNoInteractions(email);
    }

    @Test
    void multiGiftOrderCreatesOneCodePerGiftForTheBuyer() throws Exception {
        JsonNode order = new ObjectMapper().readTree("""
            {"id":"cs_4","payment_status":"paid","amount_total":2997,
             "customer_details":{"email":"buyer@mail.com"},
             "metadata":{"type":"gift","plan":"ONE_MONTH","quantity":"3"}}
            """);
        service.createFromWebPurchase(order);

        ArgumentCaptor<GiftCode> saved = ArgumentCaptor.forClass(GiftCode.class);
        verify(giftRepo, times(3)).save(saved.capture());
        assertEquals(3, saved.getAllValues().stream().map(GiftCode::getCode).distinct().count());
        assertEquals("stripe_cs_4", saved.getAllValues().get(0).getPurchaseRef());
        // No recipient on the order: each code goes to the buyer to pass along.
        verify(email, times(3)).sendGiftPurchaseReceiptEmail(eq("buyer@mail.com"), eq("1 Month"), anyString(), isNull(), anyString());
        verify(email, never()).sendGiftReceivedEmail(anyString(), any(), anyString(), anyString(), anyString());
    }

    @Test
    void multiGiftOrderMustPayForEveryGift() throws Exception {
        JsonNode order = new ObjectMapper().readTree("""
            {"id":"cs_5","payment_status":"paid","amount_total":999,
             "customer_details":{"email":"buyer@mail.com"},
             "metadata":{"type":"gift","plan":"ONE_MONTH","quantity":"3"}}
            """);
        service.createFromWebPurchase(order);
        verify(giftRepo, never()).save(any());
        verifyNoInteractions(email);
    }

    @Test
    void onlyMonthAndYearAreSoldOnTheWeb() {
        assertEquals(999, GiftPlan.webPlan("ONE_MONTH").orElseThrow().getWebPriceCents());
        assertEquals(2999, GiftPlan.webPlan("one_year").orElseThrow().getWebPriceCents());
        assertTrue(GiftPlan.webPlan("THREE_MONTHS").isEmpty());
        assertTrue(GiftPlan.webPlan("free").isEmpty());
    }
}
