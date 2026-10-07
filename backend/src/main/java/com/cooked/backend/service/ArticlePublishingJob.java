package com.cooked.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Publishes scheduled articles (checked every 15 minutes). */
@Component
@RequiredArgsConstructor
public class ArticlePublishingJob {

    private final ArticleService articles;

    @Scheduled(cron = "0 */15 * * * ?")
    public void publishDueArticles() {
        articles.publishDue();
    }
}
