package com.cooked.backend.service;

import com.cooked.backend.dto.response.RevenueSummaryResponse;

/** Revenue and subscription metrics for the admin backoffice. */
public interface AdminRevenueService {

    /**
     * Summary for the last {@code days} days (clamped to 1..365) vs the same
     * length just before, plus a daily series (at least 7 days, at most 90)
     * and an 8-month series. Field names keep their historic "30" suffix.
     */
    RevenueSummaryResponse getSummary(int days);

    /** Corrects a recorded payment (e.g. a TestFlight renewal marked SANDBOX) so revenue counts it right. */
    com.cooked.backend.dto.response.PaymentStatusResponse setPaymentStatus(java.util.UUID id, String status, String adminEmail);

    /** Same, with the reason written to the audit log. */
    com.cooked.backend.dto.response.PaymentStatusResponse setPaymentStatus(java.util.UUID id, String status, String reason, String adminEmail);

    /**
     * Payments screen. Period = [from, to] days when given, else the last
     * {@code days} days; store ALL|APPLE|GOOGLE|WEB, plan ALL|YEARLY|MONTHLY,
     * status ALL or one status, q searches name, email and transaction id.
     */
    com.cooked.backend.dto.response.PaymentListResponse listPayments(int days, java.time.LocalDate from, java.time.LocalDate to,
            String store, String plan, String status, String q, int page, int size);
}
