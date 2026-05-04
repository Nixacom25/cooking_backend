package com.cooked.backend.service;

import com.cooked.backend.dto.request.SubscriptionPaymentRequest;
import com.cooked.backend.dto.request.UpdateSubscriptionPlanRequest;
import com.cooked.backend.dto.request.IapReceiptRequest;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.dto.response.SubscriptionPaymentResponse;
import com.cooked.backend.entity.SubscriptionPlan;
import com.cooked.backend.entity.User;
import com.cooked.backend.entity.UserSubscription;
import com.cooked.backend.entity.SubscriptionType;

import java.util.List;

public interface SubscriptionService {
    SubscriptionPlan getPlan();
    SubscriptionPlan updatePlan(UpdateSubscriptionPlanRequest request);
    UserSubscription getMySubscription(String userEmail);
    MessageResponse paySubscription(String userEmail, SubscriptionPaymentRequest request);
    MessageResponse verifyReceipt(String userEmail, IapReceiptRequest request);
    List<SubscriptionPaymentResponse> getPaymentHistory(String userEmail);
    void processExpiredSubscriptions();
    
    // Legacy support methods if needed
    void activatePremium(User user, SubscriptionType type, String transactionId);
    boolean isPremium(User user);
}
