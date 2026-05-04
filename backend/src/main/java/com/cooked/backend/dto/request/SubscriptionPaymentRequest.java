package com.cooked.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class SubscriptionPaymentRequest {
    @NotNull(message = "isYearly flag is required")
    private Boolean isYearly;

    @NotBlank(message = "Card token or number is required for simulation")
    private String stripeToken;

    @Pattern(regexp = "^(0[1-9]|1[0-2])\\/?([0-9]{2})$", message = "Expiry month/year must be valid (MM/YY)")
    private String expDate;

    @Pattern(regexp = "^[0-9]{3,4}$", message = "CVC must be 3 or 4 digits")
    private String cvc;

    public SubscriptionPaymentRequest() {}

    public Boolean getIsYearly() { return isYearly; }
    public void setIsYearly(Boolean isYearly) { this.isYearly = isYearly; }
    public String getStripeToken() { return stripeToken; }
    public void setStripeToken(String stripeToken) { this.stripeToken = stripeToken; }
    public String getExpDate() { return expDate; }
    public void setExpDate(String expDate) { this.expDate = expDate; }
    public String getCvc() { return cvc; }
    public void setCvc(String cvc) { this.cvc = cvc; }
}
