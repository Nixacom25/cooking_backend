package com.cooked.backend.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ArticleRequest {
    @NotBlank @Size(max = 160)
    private String title;
    @Size(max = 180)
    private String slug;
    @Size(max = 40)
    private String category;
    @Size(max = 120)
    private String primaryKeyword;
    @Size(max = 300)
    private String summary;
    @Size(max = 100_000)
    private String body;
    @Size(max = 500)
    private String coverImage;
    @Min(0) @Max(20000)
    private Integer wordTarget;
    private LocalDateTime scheduledAt;
}
