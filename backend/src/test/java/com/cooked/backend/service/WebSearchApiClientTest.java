package com.cooked.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WebSearchApiClientTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void parsesSerperOrganicResults() throws Exception {
        List<Map<String, String>> r = WebSearchApiClient.parseSerper(json.readTree("""
                {"organic":[
                  {"title":"Chicken Tacos Recipe","link":"https://www.allrecipes.com/recipe/1/tacos/","snippet":"Easy tacos."},
                  {"title":"no link"},
                  {"title":"Ad","link":"javascript:void(0)"}]}"""));
        assertEquals(1, r.size());
        assertEquals("https://www.allrecipes.com/recipe/1/tacos/", r.get(0).get("url"));
        assertEquals("Easy tacos.", r.get(0).get("snippet"));
    }

    @Test
    void parsesBraveResultsAndStripsHighlightTags() throws Exception {
        List<Map<String, String>> r = WebSearchApiClient.parseBrave(json.readTree("""
                {"web":{"results":[
                  {"title":"Best <strong>Carbonara</strong>","url":"https://www.bbcgoodfood.com/recipes/carbonara",
                   "description":"Rich &amp; creamy <strong>carbonara</strong>"}]}}"""));
        assertEquals("Best Carbonara", r.get(0).get("title"));
        assertEquals("Rich & creamy carbonara", r.get(0).get("snippet"));
    }

    @Test
    void notConfiguredReturnsNothing() {
        assertTrue(new WebSearchApiClient().search("pasta").isEmpty());
    }
}
