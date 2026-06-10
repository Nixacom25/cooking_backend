package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatsResponse {
    private LocalDateTime accountCreatedAt;
    private LocalDateTime lastLogin;
    private long totalSessions;
    private long totalRecipes;
    private long totalCookbooks;
    private long totalScans;
    private long totalImports;
    private long savedExploreRecipes;
}
