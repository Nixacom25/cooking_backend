package com.cooked.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class IapReceiptRequest {
    @NotBlank(message = "Product ID is required")
    private String productId;

    @NotBlank(message = "Purchase token / receipt data is required")
    private String purchaseToken;

    @NotBlank(message = "Platform (IOS or ANDROID) is required")
    private String platform;

    private String packageName; // Required for Android verification
}
