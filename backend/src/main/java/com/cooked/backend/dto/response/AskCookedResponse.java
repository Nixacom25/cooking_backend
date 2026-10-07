package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AskCookedResponse {
    private String question;
    private String answer;
    /** Data sources the answer is based on ("Product analytics", "Cost Center"…). */
    private List<String> sources;
    private long tookMs;
    private String model;
}
