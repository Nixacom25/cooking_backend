package com.cooked.backend.entity;

/** Content workflow, in order. Only PUBLISHED articles are visible on the website. */
public enum ArticleStatus {
    IDEA, RESEARCH, BRIEF, DRAFT, REVIEW, APPROVED, SCHEDULED, PUBLISHED, UPDATING
}
