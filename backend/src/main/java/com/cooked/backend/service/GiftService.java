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
}
