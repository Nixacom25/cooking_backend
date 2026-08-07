package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StagiaireLeaderboardResponse {
    private UUID userId;
    private String firstname;
    private String lastname;
    private String email;
    private String photo;
    private int rank;
    private String badge; // 🥇, 🥈, 🥉, or empty
    private long totalAssigned;
    private long totalValidated;
    private long totalDeleted;
    private long totalPendingValidation;
    private long totalReturnedForCorrection;
    private long totalRemaining;
    private double validationRate; // e.g. 85.5%
    private double progressPercentage; // e.g. 72.0%
}
