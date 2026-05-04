package com.cooked.backend.repository;

import com.cooked.backend.entity.PaywallVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PaywallVariantRepository extends JpaRepository<PaywallVariant, Long> {
    Optional<PaywallVariant> findByVariantKey(String variantKey);
}
