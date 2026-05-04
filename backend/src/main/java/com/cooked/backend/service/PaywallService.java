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
        return paywallVariantRepository.findByVariantKey(variantKey)
                .orElseGet(() -> createDefaultVariant(variantKey));
    }

    private PaywallVariant createDefaultVariant(String key) {
        PaywallVariant variant = new PaywallVariant();
        variant.setVariantKey(key);
        variant.setTitle(key.equals("A") ? "Devenez un Chef Culinary Master" : "Cuisinez sans Limites avec Premium");
        variant.setSubtitle("Accès illimité à toutes les fonctions IA");
        variant.setMonthlyPriceLabel("9.99€ / mois");
        variant.setYearlyPriceLabel(key.equals("A") ? "59.99€ / an" : "49.99€ / an"); 
        variant.setCtaText("Commencer mon essai");
        variant.setDiscountLabel(key.equals("B") ? "OFFRE LIMITÉE : -20%" : "");
        variant.setFeaturesJson("[\"Génération IA illimitée\", \"Scan d'ingrédients illimité\", \"Import par lien web\", \"Zéro publicité\"]");
        
        return paywallVariantRepository.save(variant);
    }
}
