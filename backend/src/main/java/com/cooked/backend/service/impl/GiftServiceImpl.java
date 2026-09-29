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
