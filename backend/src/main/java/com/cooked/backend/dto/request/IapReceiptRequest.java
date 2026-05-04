package com.cooked.backend.dto.request;

import jakarta.validation.constraints.NotBlank;

public class IapReceiptRequest {
    @NotBlank(message = "Product ID is required")
    private String productId;

    @NotBlank(message = "Purchase token / receipt data is required")
    private String purchaseToken;

    @NotBlank(message = "Platform (IOS or ANDROID) is required")
    private String platform;

    private String packageName; // Required for Android verification

    public IapReceiptRequest() {}

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public String getPurchaseToken() { return purchaseToken; }
    public void setPurchaseToken(String purchaseToken) { this.purchaseToken = purchaseToken; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
}
