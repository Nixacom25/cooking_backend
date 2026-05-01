package com.cooked.backend.service;

import com.cooked.backend.dto.request.SubscriptionPaymentRequest;
import com.cooked.backend.dto.request.UpdateSubscriptionPlanRequest;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.entity.SubscriptionPlan;
import com.cooked.backend.entity.UserSubscription;

public interface SubscriptionService {
    SubscriptionPlan getPlan();

    SubscriptionPlan updatePlan(UpdateSubscriptionPlanRequest request);

    UserSubscription getMySubscription(String userEmail);

    MessageResponse paySubscription(String userEmail, SubscriptionPaymentRequest request);

    MessageResponse verifyReceipt(String userEmail, com.cooked.backend.dto.request.IapReceiptRequest request);

    java.util.List<com.cooked.backend.dto.response.SubscriptionPaymentResponse> getPaymentHistory(String userEmail);

    void processExpiredSubscriptions();
}
