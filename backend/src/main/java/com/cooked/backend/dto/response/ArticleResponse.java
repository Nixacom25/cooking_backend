package com.cooked.backend.dto.response;

import com.cooked.backend.entity.ArticleStatus;
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
public class ArticleResponse {
    private UUID id;
    private String title;
    private String slug;
    private ArticleStatus status;
    private String category;
    private String primaryKeyword;
    private String summary;
    /** Null in lists (only in the single-article responses). */
    private String body;
    private String coverImage;
    private Integer wordTarget;
    private int wordCount;
    private int readMinutes;
    private String owner;
    private LocalDateTime scheduledAt;
    private LocalDateTime publishedAt;
    private LocalDateTime updatedAt;
    /** Public URL once published. */
    private String url;
}
