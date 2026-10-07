package com.cooked.backend.repository;

import com.cooked.backend.entity.Article;
import com.cooked.backend.entity.ArticleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArticleRepository extends JpaRepository<Article, UUID> {

    interface StatusCount {
        ArticleStatus getStatus();
        Long getTotal();
    }

    Optional<Article> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Optional<Article> findBySlugAndStatus(String slug, ArticleStatus status);

    Page<Article> findByStatusOrderByPublishedAtDesc(ArticleStatus status, Pageable page);

    Page<Article> findByStatusOrderByUpdatedAtDesc(ArticleStatus status, Pageable page);

    Page<Article> findAllByOrderByUpdatedAtDesc(Pageable page);

    List<Article> findByStatusAndScheduledAtLessThanEqual(ArticleStatus status, LocalDateTime at);

    @Query("select a.status as status, count(a) as total from Article a group by a.status")
    List<StatusCount> countByStatus();
}
