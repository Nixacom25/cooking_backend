package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GiftCodeResponse {
    private UUID id;
    private String code;
    private String plan;
    private String planLabel;
    private String status;
    private String redeemUrl;
    private LocalDateTime createdAt;
    private LocalDateTime redeemedAt;
}
