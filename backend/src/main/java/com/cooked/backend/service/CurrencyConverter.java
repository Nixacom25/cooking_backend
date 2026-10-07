package com.cooked.backend.service;

import com.cooked.backend.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;

/** Converts entered costs to USD (the Cost Center's reporting currency) with configured rates. */
@Component
public class CurrencyConverter {

    private final Map<String, BigDecimal> usdPerUnit;

    public CurrencyConverter(@Value("${cost.fx.eur:1.08}") BigDecimal eur,
                             @Value("${cost.fx.cad:0.73}") BigDecimal cad,
                             @Value("${cost.fx.gbp:1.27}") BigDecimal gbp) {
        this.usdPerUnit = Map.of("USD", BigDecimal.ONE, "EUR", eur, "CAD", cad, "GBP", gbp);
    }

    public BigDecimal toUsd(BigDecimal amount, String currency) {
        BigDecimal rate = usdPerUnit.get(currency == null ? "" : currency.toUpperCase(Locale.ROOT));
        if (rate == null) throw new BadRequestException("Unsupported currency: " + currency + " (use " + String.join(", ", usdPerUnit.keySet()) + ")");
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }
}
