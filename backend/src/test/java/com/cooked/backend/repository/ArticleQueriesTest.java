package com.cooked.backend.repository;

import com.cooked.backend.entity.Article;
import com.cooked.backend.entity.ArticleStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ArticleQueriesTest {

    @Autowired private ArticleRepository repo;

    @Test
    void publishedAndCounts() {
        repo.save(Article.builder().title("A").slug("a").status(ArticleStatus.PUBLISHED).body("x").publishedAt(LocalDateTime.now()).build());
        repo.save(Article.builder().title("B").slug("b").status(ArticleStatus.DRAFT).build());
        repo.save(Article.builder().title("C").slug("c").status(ArticleStatus.SCHEDULED).scheduledAt(LocalDateTime.now().minusHours(1)).build());
        repo.flush();
        assertEquals(1, repo.findByStatusOrderByPublishedAtDesc(ArticleStatus.PUBLISHED, PageRequest.of(0, 10)).getTotalElements());
        assertTrue(repo.findBySlugAndStatus("b", ArticleStatus.PUBLISHED).isEmpty());
        assertEquals(3, repo.countByStatus().size());
        assertEquals(1, repo.findByStatusAndScheduledAtLessThanEqual(ArticleStatus.SCHEDULED, LocalDateTime.now()).size());
        assertTrue(repo.existsBySlug("a"));
    }
}
