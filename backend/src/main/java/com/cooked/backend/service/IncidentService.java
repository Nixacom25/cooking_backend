package com.cooked.backend.service;

import com.cooked.backend.dto.request.IncidentRequest;
import com.cooked.backend.dto.response.IncidentResponse;

import java.util.List;
import java.util.UUID;

/** Incidents declared from System health. */
public interface IncidentService {

    List<IncidentResponse> recent();

    List<IncidentResponse> open();

    IncidentResponse declare(IncidentRequest request, String adminEmail);

    IncidentResponse update(UUID id, IncidentRequest request);
}
