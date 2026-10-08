package com.cooked.backend.service;

import com.cooked.backend.dto.request.SavedViewRequest;
import com.cooked.backend.dto.response.SavedViewResponse;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Per-admin backoffice state: alerts marked as read (shared by the team) and saved views (per admin). */
public interface AdminPreferencesService {

    List<String> acknowledgedAlerts();

    /** Marks the keys as read; already-read keys are ignored. Returns how many were new. */
    int acknowledge(Collection<String> keys, String adminEmail);

    List<SavedViewResponse> views(String adminEmail, String screen);

    SavedViewResponse saveView(SavedViewRequest request, String adminEmail);

    /** Only the owner can delete a view. */
    void deleteView(UUID id, String adminEmail);
}
