package com.cooked.backend.service;

import com.cooked.backend.repository.SubscriptionPaymentRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.entity.SubscriptionPayment;
import com.cooked.backend.entity.Role;
import com.cooked.backend.entity.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
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

    private static String formatCustomerName(String firstname, String lastname) {
        String first = firstname == null ? "" : firstname.trim();
        String last = lastname == null ? "" : lastname.trim();
        String full = (first + " " + last).trim();
        return full.isEmpty() ? "Unknown" : full;
    }

    public Page<Map<String, Object>> getTransactions(Pageable pageable) {
        Page<SubscriptionPayment> payments = paymentRepository.findAll(pageable);
        return payments.map(p -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", p.getId());
            map.put("customer", formatCustomerName(p.getUser().getFirstname(), p.getUser().getLastname()));
            map.put("store", p.getStore());
            map.put("product", p.getPlanType() + " Plan");
            map.put("date", p.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")));
            map.put("revenue", "€" + p.getAmount());
            map.put("status", p.getStatus());
            return map;
        });
    }

    private static final List<SubscriptionStatus> ACTIVE_STATUSES =
            List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIAL, SubscriptionStatus.INFINITE);

    public Page<Map<String, Object>> getActiveSubscriptions(Pageable pageable) {
        return userRepository.findAllByRoleAndSubscriptionStatusIn(Role.CLIENT, ACTIVE_STATUSES, pageable)
            .map(u -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", u.getId());
                map.put("customer", formatCustomerName(u.getFirstname(), u.getLastname()));
                map.put("email", u.getEmail());
                map.put("status", u.getSubscriptionStatus().name());
                map.put("joinedDate", u.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
                return map;
            });
    }
}
