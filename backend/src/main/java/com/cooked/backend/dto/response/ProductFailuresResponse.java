package com.cooked.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** One page of failed product events (e.g. imports), newest first. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductFailuresResponse {
    private int days;
    private long failuresToday;
    private long totalElements;
    private int totalPages;
    private int page;
    private int size;
    private List<Item> items;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Item {
        private UUID id;
        /** Import domain / search query / scan variant. */
        private String source;
        private String reason;
        private Integer durationMs;
        private LocalDateTime createdAt;
        private UUID userId;
        private String userName;
        private String userEmail;
    }
}
