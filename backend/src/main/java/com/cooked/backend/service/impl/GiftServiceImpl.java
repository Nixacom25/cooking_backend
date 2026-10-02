package com.cooked.backend.service.impl;

import com.cooked.backend.dto.response.GiftCodeResponse;
import com.cooked.backend.dto.response.GiftRedeemResponse;
import com.cooked.backend.entity.*;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.GiftCodeRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.ActivityLogService;
import com.cooked.backend.service.EmailService;
import com.cooked.backend.service.GiftService;
import com.cooked.backend.service.RevenueCatApiClient;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GiftServiceImpl implements GiftService {

    /** Most gift codes one website order can buy (matches the site's stepper). */
    static final int MAX_GIFTS_PER_ORDER = 10;

    public static final String REDEEM_URL = "https://link.cookedapp.com/redeem";

    // No 0/O, 1/I/L: codes are read and typed by humans.
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final String PREFIX = "COOK";
    private static final int GROUPS = 3;
    private static final int GROUP_SIZE = 4;

    private final SecureRandom random = new SecureRandom();

    private final GiftCodeRepository giftCodeRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final ActivityLogService activityLogService;
    private final RevenueCatApiClient revenueCatApiClient;
    private final ProxyManager<byte[]> proxyManager;
    private final com.cooked.backend.service.StripeClient stripeClient;

    /**
     * One shared test code (QA). Usable once per account, grants 3 days only.
     * Set GIFT_TEST_CODE empty to disable it.
     */
    @org.springframework.beans.factory.annotation.Value("${gift.test-code:}")
    private String testCode;

    private static final java.util.regex.Pattern EMAIL =
            java.util.regex.Pattern.compile("^[A-Za-z0-9._%+'-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    @Override
    @Transactional
    public void createFromPurchase(User purchaser, GiftPlan plan, String purchaseRef, String transactionId, String store) {
        if (giftCodeRepository.existsByPurchaseRef(purchaseRef)) {
            log.info("Gift purchase {} already processed, skipping", purchaseRef);
            return;
        }

        GiftCode gift = giftCodeRepository.save(GiftCode.builder()
                .code(generateUniqueCode())
                .plan(plan)
                .purchaser(purchaser)
                .purchaseRef(purchaseRef)
                .transactionId(transactionId)
                .store(store)
                .build());

        log.info("Created gift code {} ({}) for {}", gift.getId(), plan, purchaser.getEmail());
        activityLogService.logActivity(purchaser, "Gift Purchased",
                "You bought a " + plan.getLabel() + " Cooked gift.");
        emailService.sendGiftCodeEmail(purchaser.getEmail(), purchaser.getFirstname(),
                plan.getLabel(), gift.getCode(), redeemUrl(gift.getCode()));
    }

    @Override
    @Transactional
    public void voidForRefund(String transactionId) {
        if (transactionId == null) return;
        for (GiftCode gift : giftCodeRepository.findAllByTransactionId(transactionId)) {
            if (gift.getStatus() == GiftCodeStatus.AVAILABLE) {
                gift.setStatus(GiftCodeStatus.VOIDED);
                giftCodeRepository.save(gift);
                log.info("Voided gift code {} after refund", gift.getId());
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<GiftCodeResponse> getMyGifts(String userEmail) {
        User user = findUser(userEmail);
        return giftCodeRepository.findAllByPurchaserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(g -> GiftCodeResponse.builder()
                        .id(g.getId())
                        .code(g.getCode())
                        .plan(g.getPlan().name())
                        .planLabel(g.getPlan().getLabel())
                        .status(g.getStatus().name())
                        .redeemUrl(redeemUrl(g.getCode()))
                        .createdAt(g.getCreatedAt())
                        .redeemedAt(g.getRedeemedAt())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public GiftRedeemResponse redeem(String userEmail, String rawCode) {
        User user = findUser(userEmail);

        // Throttle guesses per account (shared across instances via Redis).
        BucketConfiguration limit = BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(10))))
                .build();
        boolean allowed = proxyManager.builder()
                .build(("gift-redeem-" + user.getId()).getBytes(StandardCharsets.UTF_8), limit)
                .tryConsume(1);
        if (!allowed) {
            throw new BadRequestException("Too many attempts. Please try again in a few minutes.");
        }

        String code = normalize(rawCode);
        if (testCode != null && !testCode.isBlank() && code.equals(normalize(testCode))) {
            return redeemTestCode(user);
        }
        GiftCode gift = giftCodeRepository.findByCodeForUpdate(code)
                .orElseThrow(() -> new BadRequestException("This gift code is not valid."));

        if (gift.getStatus() == GiftCodeStatus.REDEEMED) {
            throw new BadRequestException("This gift code has already been used.");
        }
        if (gift.getStatus() == GiftCodeStatus.VOIDED) {
            throw new BadRequestException("This gift code is no longer valid.");
        }

        GiftPlan plan = gift.getPlan();

        // RevenueCat is the app's source of truth for premium: grant there
        // first. If it fails, the exception rolls back and the code stays usable.
        revenueCatApiClient.grantPromotionalEntitlement(user.getId().toString(), plan.getRevenueCatDuration());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = user.getSubscriptionExpiresAt() != null && user.getSubscriptionExpiresAt().isAfter(now)
                && user.getSubscriptionStatus() != SubscriptionStatus.EXPIRED
                ? user.getSubscriptionExpiresAt() : now;
        LocalDateTime premiumUntil = base.plusMonths(plan.getMonths());

        if (user.getSubscriptionStatus() != SubscriptionStatus.INFINITE) {
            user.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
            user.setSubscriptionExpiresAt(premiumUntil);
            if (user.getSubscriptionType() == null || user.getSubscriptionType() == SubscriptionType.NONE) {
                user.setSubscriptionType(plan == GiftPlan.ONE_YEAR ? SubscriptionType.YEARLY : SubscriptionType.MONTHLY);
            }
            userRepository.save(user);
        }

        gift.setStatus(GiftCodeStatus.REDEEMED);
        gift.setRedeemedBy(user);
        gift.setRedeemedAt(now);
        giftCodeRepository.save(gift);

        activityLogService.logActivity(user, "Gift Redeemed",
                "You redeemed a " + plan.getLabel() + " Cooked gift.");
        log.info("Gift code {} redeemed by {}", gift.getId(), user.getEmail());

        return GiftRedeemResponse.builder()
                .planLabel(plan.getLabel())
                .months(plan.getMonths())
                .premiumUntil(premiumUntil)
                .build();
    }

    /** QA test code: 3 days of Premium, once per account. */
    private GiftRedeemResponse redeemTestCode(User user) {
        String ref = "test_" + user.getId();
        if (giftCodeRepository.existsByPurchaseRef(ref)) {
            throw new BadRequestException("This test code was already used on this account.");
        }
        revenueCatApiClient.grantPromotionalEntitlement(user.getId().toString(), "three_day");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime premiumUntil = now.plusDays(3);
        if (user.getSubscriptionStatus() != SubscriptionStatus.INFINITE
                && (user.getSubscriptionExpiresAt() == null || user.getSubscriptionExpiresAt().isBefore(premiumUntil))) {
            user.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
            user.setSubscriptionExpiresAt(premiumUntil);
            userRepository.save(user);
        }
        // Record the use (unique purchase_ref blocks a second use).
        giftCodeRepository.save(GiftCode.builder()
                .code("TEST" + user.getId().toString().replace("-", "").substring(0, 28))
                .plan(GiftPlan.ONE_MONTH)
                .status(GiftCodeStatus.REDEEMED)
                .purchaseRef(ref)
                .store("TEST")
                .redeemedBy(user)
                .redeemedAt(now)
                .build());
        log.info("Test gift code used by {}", user.getEmail());
        return GiftRedeemResponse.builder().planLabel("3-day test").months(0).premiumUntil(premiumUntil).build();
    }

    @Override
    public List<java.util.Map<String, Object>> webPlans() {
        return java.util.Arrays.stream(GiftPlan.values())
                .filter(GiftPlan::isSoldOnWeb)
                .map(p -> java.util.Map.<String, Object>of(
                        "plan", p.name(),
                        "label", p.getLabel(),
                        "priceCents", p.getWebPriceCents(),
                        "currency", "USD"))
                .toList();
    }

    @Override
    public String startWebCheckout(String planName, String purchaserEmail, String recipientEmail,
                                   String senderName, int quantity, String clientKey) {
        // Throttle checkout creation per client (anti-abuse of Stripe sessions).
        BucketConfiguration limit = BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(10))))
                .build();
        if (!proxyManager.builder()
                .build(("gift-checkout-" + clientKey).getBytes(StandardCharsets.UTF_8), limit)
                .tryConsume(1)) {
            throw new BadRequestException("Too many attempts. Please try again in a few minutes.");
        }

        GiftPlan plan = GiftPlan.webPlan(planName)
                .orElseThrow(() -> new BadRequestException("Please choose a gift plan."));
        if (quantity < 1 || quantity > MAX_GIFTS_PER_ORDER) {
            throw new BadRequestException("You can buy between 1 and " + MAX_GIFTS_PER_ORDER + " gifts at a time.");
        }
        String buyer = cleanEmail(purchaserEmail, "Please enter your email.");
        // Optional: without it, the codes are emailed to the buyer to pass along.
        String recipient = recipientEmail == null || recipientEmail.isBlank()
                ? null
                : cleanEmail(recipientEmail, "Please enter a valid email for your friend.");
        // No control characters (the name ends up in an email subject).
        String sender = senderName == null ? null : senderName.replaceAll("\\p{Cntrl}", "").trim();
        if (sender != null && sender.length() > 60) sender = sender.substring(0, 60);

        return stripeClient.createGiftCheckout(plan, quantity, buyer, recipient, sender);
    }

    @Override
    @Transactional
    public void createFromWebPurchase(com.fasterxml.jackson.databind.JsonNode session) {
        String sessionId = session.path("id").asText("");
        String ref = "stripe_" + sessionId;
        if (sessionId.isBlank() || giftCodeRepository.existsByPurchaseRef(ref)) return; // retried webhook

        com.fasterxml.jackson.databind.JsonNode meta = session.path("metadata");
        if (!"gift".equals(meta.path("type").asText())) return;
        if (!"paid".equals(session.path("payment_status").asText())) return;

        GiftPlan plan = GiftPlan.webPlan(meta.path("plan").asText(null))
                .orElseThrow(() -> new IllegalStateException("Unknown gift plan in session " + sessionId));
        int quantity = meta.path("quantity").asInt(1);
        if (quantity < 1 || quantity > MAX_GIFTS_PER_ORDER) {
            log.error("Gift session {} has an invalid quantity {}", sessionId, quantity);
            return;
        }
        // Defense in depth: the amount actually paid must match our price.
        long expected = plan.getWebPriceCents() * (long) quantity;
        if (session.path("amount_total").asLong(-1) != expected) {
            log.error("Gift session {} paid {} instead of {}", sessionId,
                    session.path("amount_total").asLong(-1), expected);
            return;
        }

        String buyer = session.path("customer_details").path("email").asText(null);
        if (buyer == null || buyer.isBlank()) buyer = session.path("customer_email").asText(null);
        String recipient = meta.path("recipient_email").asText(null);
        String sender = meta.path("sender_name").asText(null);

        User purchaser = buyer != null ? userRepository.findByEmail(buyer).orElse(null) : null;
        for (int i = 0; i < quantity; i++) {
            GiftCode gift = giftCodeRepository.save(GiftCode.builder()
                    .code(generateUniqueCode())
                    .plan(plan)
                    .purchaser(purchaser)
                    .purchaserEmail(buyer)
                    .recipientEmail(recipient)
                    .senderName(sender)
                    // First code keeps the session ref (idempotency check above).
                    .purchaseRef(i == 0 ? ref : ref + "_" + (i + 1))
                    .transactionId(session.path("payment_intent").asText(null))
                    .store("Stripe")
                    .build());
            log.info("Created web gift code {} ({}, {}/{})", gift.getId(), plan, i + 1, quantity);

            String url = redeemUrl(gift.getCode());
            if (buyer != null) {
                emailService.sendGiftPurchaseReceiptEmail(buyer, plan.getLabel(), gift.getCode(), recipient, url);
            }
            if (recipient != null && !recipient.equalsIgnoreCase(buyer)) {
                emailService.sendGiftReceivedEmail(recipient, sender, plan.getLabel(), gift.getCode(), url);
            }
        }
    }

    private String cleanEmail(String raw, String message) {
        String email = raw == null ? "" : raw.trim().toLowerCase();
        if (email.length() > 254 || !EMAIL.matcher(email).matches()) {
            throw new BadRequestException(message);
        }
        return email;
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    /** Accepts "cook-abcd-efgh-jkmn", "COOKABCDEFGHJKMN", extra spaces, etc. */
    static String normalize(String raw) {
        if (raw == null) throw new BadRequestException("Please enter a gift code.");
        String compact = raw.toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (compact.startsWith(PREFIX)) compact = compact.substring(PREFIX.length());
        if (compact.length() != GROUPS * GROUP_SIZE) {
            throw new BadRequestException("This gift code is not valid.");
        }
        StringBuilder sb = new StringBuilder(PREFIX);
        for (int i = 0; i < GROUPS; i++) {
            sb.append('-').append(compact, i * GROUP_SIZE, (i + 1) * GROUP_SIZE);
        }
        return sb.toString();
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder(PREFIX);
            for (int g = 0; g < GROUPS; g++) {
                sb.append('-');
                for (int i = 0; i < GROUP_SIZE; i++) {
                    sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
                }
            }
            String code = sb.toString();
            if (!giftCodeRepository.existsByCode(code)) return code;
        }
        throw new IllegalStateException("Could not generate a unique gift code");
    }

    private static String redeemUrl(String code) {
        return REDEEM_URL + "?code=" + code;
    }
}
