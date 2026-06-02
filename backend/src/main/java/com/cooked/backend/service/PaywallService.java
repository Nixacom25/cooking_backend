package com.cooked.backend.service;

import com.cooked.backend.entity.PaywallVariant;
import com.cooked.backend.entity.User;
import com.cooked.backend.repository.PaywallVariantRepository;
import org.springframework.stereotype.Service;

@Service
public class PaywallService {

    private final PaywallVariantRepository paywallVariantRepository;

    public PaywallService(PaywallVariantRepository paywallVariantRepository) {
        this.paywallVariantRepository = paywallVariantRepository;
    }

    /**
     * Retrieves the paywall configuration assigned to the user (A/B Testing).
     * 50/50 split based on UUID hash.
     */
    public PaywallVariant getConfigurationForUser(User user) {
        String variantKey = (user.getId().hashCode() % 2 == 0) ? "A" : "B";
        String lang = "en";
        
        PaywallVariant variant = paywallVariantRepository.findByVariantKey(variantKey)
                .orElse(null);
        
        if (variant == null) {
            return createDefaultVariant(variantKey, lang);
        }
        
        // Always sync with user language preference
        return updateVariantLanguage(variant, lang);
    }

    public PaywallVariant getOfferConfiguration() {
        String lang = "en"; // Default or detect from context if possible
        PaywallVariant variant = paywallVariantRepository.findByVariantKey("OFFER")
                .orElse(null);
        
        if (variant == null) {
            return createDefaultVariant("OFFER", lang);
        }
        
        return updateVariantLanguage(variant, lang);
    }

    public PaywallVariant getDefaultConfiguration() {
        PaywallVariant variant = paywallVariantRepository.findByVariantKey("A")
                .orElse(null);
        
        if (variant == null) {
            return createDefaultVariant("A", "en");
        }
        
        return updateVariantLanguage(variant, "en");
    }

    private PaywallVariant updateVariantLanguage(PaywallVariant variant, String lang) {
        String key = variant.getVariantKey();
        boolean isOffer = "OFFER".equals(key);

        // Forced English as per user request
        variant.setTitle(isOffer ? "Special comeback offer" : "Start your 3-day FREE trial to continue.");
        variant.setSubtitle(isOffer ? "This is your last chance!" : "Unlock all Cooked features");
        variant.setMonthlyPriceLabel(isOffer ? "$9.99 / month" : "$9.99 / month");
        variant.setYearlyPriceLabel(isOffer ? "$19.99 / year" : "$2.49 / mo"); 
        variant.setCtaText(isOffer ? "Unlock Premium for $19.99" : "Subscribe now");
        variant.setDiscountLabel(isOffer ? "LIMITED OFFER: 33% OFF" : "");
        variant.setFeaturesJson("[\"Unlimited AI Generation\", \"Unlimited Ingredient Scan\", \"TikTok/IG Recipe Import\"]");
        
        return paywallVariantRepository.save(variant);
    }

    private PaywallVariant createDefaultVariant(String key, String lang) {
        PaywallVariant variant = new PaywallVariant();
        variant.setVariantKey(key);
        return updateVariantLanguage(variant, lang);
    }
}
