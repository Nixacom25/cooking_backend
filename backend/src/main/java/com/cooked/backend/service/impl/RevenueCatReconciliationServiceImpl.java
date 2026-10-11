package com.cooked.backend.service.impl;

import com.cooked.backend.entity.RevenueCatPendingEvent;
import com.cooked.backend.entity.SubscriptionPayment;
import com.cooked.backend.entity.SubscriptionPlan;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.repository.RevenueCatPendingEventRepository;
import com.cooked.backend.repository.SubscriptionPaymentRepository;
import com.cooked.backend.repository.SubscriptionPlanRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.ActivityLogService;
import com.cooked.backend.service.RevenueCatApiClient;
import com.cooked.backend.service.RevenueCatReconciliationService;
import com.cooked.backend.util.StoreDates;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Slf4j
@Service
public class RevenueCatReconciliationServiceImpl implements RevenueCatReconciliationService {

    static final Set<String> REVENUE_TYPES = Set.of("INITIAL_PURCHASE", "RENEWAL", "NON_RENEWING_PURCHASE");

    private final RevenueCatPendingEventRepository pending;
    private final SubscriptionPaymentRepository payments;
    private final SubscriptionPlanRepository plans;
    private final UserRepository users;
    private final RevenueCatApiClient revenueCat;
    private final ActivityLogService activityLog;
    private final RevenueCatReconciliationServiceImpl self;
    private final TransactionTemplate tx;
    private final ObjectMapper json = new ObjectMapper();

    private volatile SyncStatus last = new SyncStatus(false, null, null, 0, 0, 0, 0, 0, null, 0, false);

    public RevenueCatReconciliationServiceImpl(RevenueCatPendingEventRepository pending, SubscriptionPaymentRepository payments,
                                               SubscriptionPlanRepository plans, UserRepository users, RevenueCatApiClient revenueCat,
                                               ActivityLogService activityLog, @Lazy RevenueCatReconciliationServiceImpl self,
                                               PlatformTransactionManager txManager) {
        this.pending = pending;
        this.payments = payments;
        this.plans = plans;
        this.users = users;
        this.revenueCat = revenueCat;
        this.activityLog = activityLog;
        this.self = self;
        this.tx = new TransactionTemplate(txManager);
    }

    // --- webhook events for unknown users ----------------------------------------------------

    @Override
    public void savePending(Map<String, Object> event) {
        try {
            Object id = event.get("id");
            if (id == null || pending.existsByEventId(id.toString())) return;
            StringBuilder ids = new StringBuilder(",");
            for (Object v : candidateIds(event)) ids.append(v).append(',');
            pending.save(RevenueCatPendingEvent.builder().eventId(id.toString()).eventType(String.valueOf(event.get("type")))
                    .appUserIds(ids.toString()).payload(json.writeValueAsString(event)).build());
        } catch (Exception e) {
            log.warn("Could not keep RevenueCat event for later: {}", e.getMessage());
        }
    }

    static List<String> candidateIds(Map<String, Object> event) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String k : List.of("app_user_id", "original_app_user_id")) if (event.get(k) != null) ids.add(event.get(k).toString());
        if (event.get("aliases") instanceof List<?> l) l.forEach(a -> { if (a != null) ids.add(a.toString()); });
        return new ArrayList<>(ids);
    }

    @Override
    public int resolveFor(User user) {
        if (user == null || user.getId() == null) return 0;
        Set<String> ids = new LinkedHashSet<>();
        ids.add(user.getId().toString());
        if (user.getRevenueCatCustomerId() != null && !user.getRevenueCatCustomerId().isBlank()) ids.add(user.getRevenueCatCustomerId());
        int done = 0;
        for (String id : ids) {
            for (RevenueCatPendingEvent e : pending.findOpenFor(id)) {
                try {
                    Map<String, Object> event = json.readValue(e.getPayload(), new TypeReference<>() {});
                    if (REVENUE_TYPES.contains(e.getEventType().toUpperCase())) recordFromEvent(event, user);
                    e.setResolvedAt(LocalDateTime.now());
                    e.setResolvedUserId(user.getId());
                    pending.save(e);
                    done++;
                } catch (Exception ex) {
                    log.warn("Pending RevenueCat event {} not applied: {}", e.getEventId(), ex.getMessage());
                }
            }
        }
        if (done > 0) log.info("Applied {} pending RevenueCat event(s) to {}", done, user.getEmail());
        return done;
    }

    /** Same record as the webhook (reference rc_<event id>), at the real purchase time. */
    void recordFromEvent(Map<String, Object> event, User user) {
        String ref = "rc_" + event.get("id");
        if (payments.existsByStripePaymentId(ref)) return;
        Object price = event.get("price");
        String productId = event.get("product_id") == null ? "" : event.get("product_id").toString();
        LocalDateTime at = epoch(event.get("purchased_at_ms"), event.get("event_timestamp_ms"));
        SubscriptionPayment p = new SubscriptionPayment();
        p.setUser(user);
        p.setAmount(price instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : planPrice(productId));
        p.setPlanType(productId.toLowerCase().contains("year") ? "YEARLY" : "MONTHLY");
        p.setStatus("SANDBOX".equalsIgnoreCase(String.valueOf(event.get("environment"))) ? "SANDBOX" : "SUCCESS");
        p.setStripePaymentId(ref);
        p.setStore(store(String.valueOf(event.get("store"))));
        save(p, at);
    }

    // --- sync from RevenueCat -------------------------------------------------------------------

    @Override
    public synchronized SyncStatus startSync(String adminEmail) {
        if (!revenueCat.isConfigured()) throw new BadRequestException("Set the RevenueCat secret key (V1) on the server first.");
        if (last.running()) return status();
        last = new SyncStatus(true, LocalDateTime.now(), null, 0, 0, 0, 0, 0, null, pending.countByResolvedAtIsNull(), true);
        users.findByEmail(adminEmail).ifPresent(a -> activityLog.logActivity(a, "Payments synced from RevenueCat", "Started", null, "PAYMENT"));
        self.runSync();
        return status();
    }

    @Async
    public void runSync() {
        List<User> candidates = users.findPossibleSubscribers();
        int checked = 0, added = 0, resolved = 0, errors = 0;
        String lastError = null;
        LocalDateTime started = last.startedAt();
        for (User u : candidates) {
            try {
                resolved += resolveFor(u);
                added += reconcile(u);
            } catch (Exception e) {
                errors++;
                lastError = u.getEmail() + ": " + e.getMessage();
            }
            checked++;
            last = new SyncStatus(true, started, null, candidates.size(), checked, added, resolved, errors, lastError, pending.countByResolvedAtIsNull(), true);
            try { Thread.sleep(120); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
        }
        last = new SyncStatus(false, started, LocalDateTime.now(), candidates.size(), checked, added, resolved, errors, lastError, pending.countByResolvedAtIsNull(), true);
        log.info("RevenueCat payment sync: {} users, {} payments added, {} pending events applied, {} errors", checked, added, resolved, errors);
    }

    /** Adds the real (non-sandbox, non-trial) purchases RevenueCat knows and we don't. */
    int reconcile(User u) {
        JsonNode sub = revenueCat.fetchSubscriber(u.getId().toString());
        if (!sub.path("subscriptions").fields().hasNext() && u.getRevenueCatCustomerId() != null && !u.getRevenueCatCustomerId().isBlank()) {
            sub = revenueCat.fetchSubscriber(u.getRevenueCatCustomerId()); // purchase never aliased to our id
        }
        int added = 0;
        for (Iterator<Map.Entry<String, JsonNode>> it = sub.path("subscriptions").fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            if (addFromSubscription(u, e.getKey(), e.getValue())) added++;
        }
        return added;
    }

    boolean addFromSubscription(User u, String productId, JsonNode s) {
        if (s.path("is_sandbox").asBoolean(false)) return false;
        if ("trial".equalsIgnoreCase(s.path("period_type").asText(""))) return false; // no money yet
        LocalDateTime at = StoreDates.parse(s.path("purchase_date").asText(null));
        if (at == null) return false;
        if (payments.existsRealPaymentBetween(u.getId(), at.minusHours(36), at.plusHours(36))) return false;
        String txn = s.path("store_transaction_id").asText("");
        String ref = "rc_sync_" + (txn.isBlank() ? productId + "_" + s.path("purchase_date").asText() : txn);
        if (payments.existsByStripePaymentId(ref)) return false;
        JsonNode price = s.path("price").path("amount");
        SubscriptionPayment p = new SubscriptionPayment();
        p.setUser(u);
        p.setAmount(price.isNumber() && price.asDouble() > 0 ? BigDecimal.valueOf(price.asDouble()) : planPrice(productId));
        p.setPlanType(productId.toLowerCase().contains("year") ? "YEARLY" : "MONTHLY");
        p.setStatus(s.hasNonNull("refunded_at") ? "REFUNDED" : "SUCCESS");
        p.setStripePaymentId(ref);
        p.setStore(store(s.path("store").asText("")));
        save(p, at);
        return true;
    }

    private void save(SubscriptionPayment p, LocalDateTime at) {
        tx.executeWithoutResult(t -> {
            SubscriptionPayment saved = payments.save(p);
            if (at != null) payments.setCreatedAt(saved.getId(), at);
        });
    }

    private BigDecimal planPrice(String productId) {
        SubscriptionPlan plan = plans.findAll().stream().findFirst().orElse(null);
        if (plan == null) return BigDecimal.ZERO;
        BigDecimal v = productId.toLowerCase().contains("year") ? plan.getYearlyPrice() : plan.getMonthlyPrice();
        return v == null ? BigDecimal.ZERO : v;
    }

    static String store(String rc) {
        String s = rc == null ? "" : rc.toUpperCase();
        return s.contains("APP_STORE") || s.contains("MAC_APP_STORE") ? "Apple" : s.contains("PLAY_STORE") ? "Google" : s.isBlank() || s.equals("NULL") ? "Google" : "STRIPE";
    }

    private static LocalDateTime epoch(Object... values) {
        for (Object v : values) if (v instanceof Number n) return LocalDateTime.ofInstant(Instant.ofEpochMilli(n.longValue()), ZoneId.systemDefault());
        return null;
    }

    @Override
    public SyncStatus status() {
        SyncStatus s = last;
        return new SyncStatus(s.running(), s.startedAt(), s.finishedAt(), s.usersTotal(), s.usersChecked(), s.paymentsAdded(), s.pendingResolved(),
                s.errors(), s.lastError(), pending.countByResolvedAtIsNull(), revenueCat.isConfigured());
    }
}
