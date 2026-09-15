package com.cooked.backend.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisionRankResult {
    private int bestIndex; // -1 if no candidate is a reasonable match
    private double confidence; // 0.0 - 1.0
    private String reason;
}
