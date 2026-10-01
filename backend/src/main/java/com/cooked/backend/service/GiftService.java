package com.cooked.backend.service;

import com.cooked.backend.dto.response.GiftCodeResponse;
import com.cooked.backend.dto.response.GiftRedeemResponse;
import com.cooked.backend.entity.GiftPlan;
import com.cooked.backend.entity.User;

import java.util.List;

public interface GiftService {
    /** Called from the RevenueCat webhook once the store confirmed the purchase. Idempotent. */
    void createFromPurchase(User purchaser, GiftPlan plan, String purchaseRef, String transactionId, String store);

    /** Refund of a gift purchase: voids its code if it hasn't been used yet. */
    void voidForRefund(String transactionId);

    List<GiftCodeResponse> getMyGifts(String userEmail);

    GiftRedeemResponse redeem(String userEmail, String rawCode);

    /** Plans sold on the website, with their server-side prices. */
    java.util.List<java.util.Map<String, Object>> webPlans();

    /** Validates the website form and returns a Stripe Checkout URL. */
    String startWebCheckout(String plan, String purchaserEmail, String recipientEmail, String senderName, String clientKey);

    /** Stripe confirmed payment (verified webhook): create + email the code. Idempotent. */
    void createFromWebPurchase(com.fasterxml.jackson.databind.JsonNode checkoutSession);
}
