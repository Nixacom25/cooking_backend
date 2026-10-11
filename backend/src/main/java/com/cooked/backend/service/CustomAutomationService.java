package com.cooked.backend.service;

import com.cooked.backend.dto.request.CustomAutomationRequest;
import com.cooked.backend.dto.response.AutomationTemplateResponse;
import com.cooked.backend.dto.response.CustomAutomationResponse;

import java.util.List;
import java.util.UUID;

/**
 * No-code automations (Growth → Automations). A flow can only be activated
 * after a dry run made since its last change; emails respect unsubscribes
 * and the 2-per-week cap; each user is reached once per flow (30 days for
 * "inactive" flows).
 */
public interface CustomAutomationService {

    List<AutomationTemplateResponse> templates();

    List<CustomAutomationResponse> list();

    CustomAutomationResponse get(UUID id);

    CustomAutomationResponse create(CustomAutomationRequest request, String adminEmail);

    /** Editing an active flow pauses it until a new dry run and activation. */
    CustomAutomationResponse update(UUID id, CustomAutomationRequest request, String adminEmail);

    /** Simulates the flow on real data. Nothing is sent. */
    CustomAutomationResponse dryRun(UUID id);

    CustomAutomationResponse activate(UUID id, String adminEmail);

    CustomAutomationResponse pause(UUID id, String adminEmail);

    void delete(UUID id, String adminEmail);
}
