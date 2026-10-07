package com.cooked.backend.service;

import com.cooked.backend.dto.response.AutomationResponse;

import java.util.List;

/** What the backend automates and how each automation has been running. */
public interface AdminAutomationService {
    List<AutomationResponse> automations();
}
