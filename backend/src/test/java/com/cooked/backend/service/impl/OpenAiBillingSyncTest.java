package com.cooked.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiBillingSyncTest {

    @Test
    void parsesDailyBucketsPerLineItem() throws Exception {
        String json = """
                {"object":"page","data":[
                  {"object":"bucket","start_time":1759708800,"end_time":1759795200,"results":[
                    {"object":"organization.costs.result","amount":{"value":0.25,"currency":"usd"},"line_item":"gpt-4o-mini, input"},
                    {"object":"organization.costs.result","amount":{"value":0.05,"currency":"usd"},"line_item":"gpt-4o-mini, input"},
                    {"object":"organization.costs.result","amount":{"value":0,"currency":"usd"},"line_item":"dall-e-3"},
                    {"object":"organization.costs.result","amount":{"value":1.5,"currency":"usd"},"line_item":null}]}],
                 "has_more":false}""";
        var rows = OpenAiBillingSync.parse(new ObjectMapper().readTree(json));
        assertEquals(2, rows.size());
        var input = rows.stream().filter(r -> r.getLineItem().equals("gpt-4o-mini, input")).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("0.30").compareTo(input.getAmountUsd()));
        assertEquals(LocalDate.of(2025, 10, 6), input.getCostDay());
        assertEquals("OpenAI", input.getProvider());
        assertTrue(rows.stream().anyMatch(r -> r.getLineItem().equals("Other usage")));
    }
}
