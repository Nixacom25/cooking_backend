package com.cooked.backend.service;

import com.cooked.backend.dto.response.AskCookedResponse;

/** Natural-language questions about Cooked, answered from read-only admin data. */
public interface AskCookedService {

    AskCookedResponse ask(String question, String adminEmail);
}
