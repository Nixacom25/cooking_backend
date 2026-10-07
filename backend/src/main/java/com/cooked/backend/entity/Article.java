package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** A website article (blog), written in the backoffice; body is Markdown. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "articles", uniqueConstraints = @UniqueConstraint(name = "uk_article_slug", columnNames = "slug"),
        indexes = @Index(name = "idx_articles_status", columnList = "status, publishedAt"))
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 180)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ArticleStatus status;

    /** Blog category ("Kitchen Tips", "Recipes"…). */
    @Column(length = 40)
    private String category;

    @Column(length = 120)
    private String primaryKeyword;

    /** Meta description / card summary. */
    @Column(length = 300)
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(length = 500)
    private String coverImage;

    private Integer wordTarget;

    @Column(length = 120)
    private String owner;

    private LocalDateTime scheduledAt;

    private LocalDateTime publishedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
