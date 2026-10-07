package com.cooked.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SeoParsingTest {

    @Test
    void parsesSearchConsoleRowsAndBlogSlugs() throws Exception {
        var rows = GoogleSearchConsoleClient.parse(new ObjectMapper().readTree(
                "{\"rows\":[{\"keys\":[\"https://cookedapp.com/blog/pasta\"],\"clicks\":12,\"impressions\":300,\"ctr\":0.04,\"position\":7.26}]}"));
        assertEquals(1, rows.size());
        assertEquals(4.0, rows.get(0).ctr());
        assertEquals(7.3, rows.get(0).position());
        assertEquals("pasta", SeoServiceImpl.slugOf("https://cookedapp.com/blog/pasta/?utm=x").orElseThrow());
        assertTrue(SeoServiceImpl.slugOf("https://cookedapp.com/gift").isEmpty());
        assertTrue(GoogleSearchConsoleClient.parse(new ObjectMapper().readTree("{}")).isEmpty());
    }
}
