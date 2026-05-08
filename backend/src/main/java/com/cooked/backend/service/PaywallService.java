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
     * Récupère la configuration du paywall assignée à l'utilisateur (A/B Testing).
     * Répartition 50/50 basée sur le hash du UUID.
     */
    public PaywallVariant getConfigurationForUser(User user) {
        String variantKey = (user.getId().hashCode() % 2 == 0) ? "A" : "B";
        String lang = (user.getLanguage() != null) ? user.getLanguage().toLowerCase() : "en";
        
        PaywallVariant variant = paywallVariantRepository.findByVariantKey(variantKey)
                .orElse(null);
        
        if (variant == null) {
            return createDefaultVariant(variantKey, lang);
        }
        
        // Always sync with user language preference
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
        if ("fr".equals(lang)) {
            variant.setTitle(key.equals("A") ? "Devenez un Chef Culinary Master" : "Cuisinez sans Limites avec Premium");
            variant.setSubtitle("Accès illimité à toutes les fonctions IA");
            variant.setMonthlyPriceLabel("9.99€ / mois");
            variant.setYearlyPriceLabel(key.equals("A") ? "59.99€ / an" : "49.99€ / an"); 
            variant.setCtaText("Commencer mon essai");
            variant.setDiscountLabel(key.equals("B") ? "OFFRE LIMITÉE : -20%" : "");
            variant.setFeaturesJson("[\"Génération IA illimitée\", \"Scan d'ingrédients illimité\", \"Import par lien web\", \"Zéro publicité\"]");
        } else {
            variant.setTitle(key.equals("A") ? "Become a Culinary Master Chef" : "Cook without Limits with Premium");
            variant.setSubtitle("Unlimited access to all AI features");
            variant.setMonthlyPriceLabel("9.99€ / month");
            variant.setYearlyPriceLabel(key.equals("A") ? "59.99€ / year" : "49.99€ / year"); 
            variant.setCtaText("Start my trial");
            variant.setDiscountLabel(key.equals("B") ? "LIMITED OFFER: -20%" : "");
            variant.setFeaturesJson("[\"Unlimited AI Generation\", \"Unlimited Ingredient Scan\", \"Import via Web Link\", \"Zero Ads\"]");
        }
        return paywallVariantRepository.save(variant);
    }

    private PaywallVariant createDefaultVariant(String key, String lang) {
        PaywallVariant variant = new PaywallVariant();
        variant.setVariantKey(key);
        return updateVariantLanguage(variant, lang);
    }
}
