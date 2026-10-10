package com.cooked.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Payments screen: one page of transactions plus the counts per status (tabs)
 * and the KPIs for the same filters. Only SUCCESS counts as revenue.
 */
public record PaymentListResponse(
        List<Row> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        Map<String, Long> statusCounts,
        BigDecimal revenue,
        BigDecimal revenuePrev,
        long payments,
        long paymentsPrev,
        BigDecimal refunds,
        long refundsCount,
        BigDecimal refundsPrev,
        long sandbox,
        String currency) {

    public record Row(UUID id, LocalDateTime createdAt, UUID userId, String name, String email,
                      String store, String planType, BigDecimal amount, String status, String reference) {
    }
}
