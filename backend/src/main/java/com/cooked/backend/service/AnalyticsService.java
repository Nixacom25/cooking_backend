package com.cooked.backend.service;

import com.cooked.backend.repository.SubscriptionPaymentRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.entity.SubscriptionPayment;
import com.cooked.backend.entity.SubscriptionStatus;
import com.cooked.backend.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.HashMap;
import java.time.format.DateTimeFormatter;

@Service
public class AnalyticsService {

    private final SubscriptionPaymentRepository paymentRepository;
    private final UserRepository userRepository;

    public AnalyticsService(SubscriptionPaymentRepository paymentRepository, UserRepository userRepository) {
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
    }

    public Page<Map<String, Object>> getTransactions(Pageable pageable) {
        Page<SubscriptionPayment> payments = paymentRepository.findAll(pageable);
        return payments.map(p -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", p.getId());
            map.put("customer", p.getUser().getFirstname() + " " + p.getUser().getLastname());
            map.put("store", p.getStore());
            map.put("product", p.getPlanType() + " Plan");
            map.put("date", p.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")));
            map.put("revenue", "€" + p.getAmount());
            map.put("status", p.getStatus());
            return map;
        });
    }

    public Page<Map<String, Object>> getActiveSubscriptions(Pageable pageable) {
        // Here we just fetch users who have a PREMIUM status
        return userRepository.findAllByRole(Role.CLIENT, pageable)
            .map(u -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", u.getId());
                map.put("customer", u.getFirstname() + " " + u.getLastname());
                map.put("email", u.getEmail());
                map.put("status", u.getSubscriptionStatus().name());
                map.put("joinedDate", u.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
                return map;
            });
    }
}
