package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.ArticleRequest;
import com.cooked.backend.entity.Article;
import com.cooked.backend.entity.ArticleStatus;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.repository.ArticleRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ArticleServiceImplTest {

    private final ArticleRepository repo = mock(ArticleRepository.class);
    private final ArticleServiceImpl service = new ArticleServiceImpl(repo, "https://cookedapp.com/");

    @Test
    void slugsAreCleanAndUnique() {
        assertEquals("creme-brulee-a-la-maison-12-ways", ArticleServiceImpl.slugify("Crème brûlée à la maison: 12 ways!"));
        when(repo.existsBySlug("pasta")).thenReturn(true);
        when(repo.existsBySlug("pasta-2")).thenReturn(false);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        ArticleRequest r = new ArticleRequest();
        r.setTitle("Pasta");
        var created = service.create(r, "admin@cooked.app");
        assertEquals("pasta-2", created.getSlug());
        assertEquals(ArticleStatus.IDEA, created.getStatus());
        assertNull(created.getUrl());                                    // not public yet
    }

    @Test
    void publishingNeedsContentAndSetsDate() {
        UUID id = UUID.randomUUID();
        Article a = Article.builder().id(id).title("T").slug("t").status(ArticleStatus.APPROVED).build();
        when(repo.findById(id)).thenReturn(Optional.of(a));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThrows(BadRequestException.class, () -> service.moveTo(id, ArticleStatus.PUBLISHED));
        a.setBody("Some **content** here");
        a.setSummary("Summary");
        assertThrows(BadRequestException.class, () -> service.moveTo(id, ArticleStatus.SCHEDULED));   // no future date
        var pub = service.moveTo(id, ArticleStatus.PUBLISHED);
        assertNotNull(pub.getPublishedAt());
        assertEquals("https://cookedapp.com/blog/t", pub.getUrl());
        assertEquals(3, pub.getWordCount());
    }

    @Test
    void publishesDueScheduledArticles() {
        Article due = Article.builder().title("Due").slug("due").status(ArticleStatus.SCHEDULED).scheduledAt(LocalDateTime.now().minusMinutes(1)).build();
        when(repo.findByStatusAndScheduledAtLessThanEqual(eq(ArticleStatus.SCHEDULED), any())).thenReturn(List.of(due));
        assertEquals(1, service.publishDue());
        assertEquals(ArticleStatus.PUBLISHED, due.getStatus());
        assertNotNull(due.getPublishedAt());
    }
}
