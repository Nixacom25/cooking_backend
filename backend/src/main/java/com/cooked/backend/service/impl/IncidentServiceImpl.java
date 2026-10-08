package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.IncidentRequest;
import com.cooked.backend.dto.response.IncidentResponse;
import com.cooked.backend.entity.Incident;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.IncidentRepository;
import com.cooked.backend.service.IncidentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IncidentServiceImpl implements IncidentService {

    private final IncidentRepository incidents;

    @Override
    @Transactional(readOnly = true)
    public List<IncidentResponse> recent() {
        return incidents.findTop50ByOrderByCreatedAtDesc().stream().map(IncidentServiceImpl::view).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncidentResponse> open() {
        return incidents.findByStatusNotOrderByCreatedAtDesc("RESOLVED").stream().map(IncidentServiceImpl::view).toList();
    }

    @Override
    @Transactional
    public IncidentResponse declare(IncidentRequest r, String adminEmail) {
        Incident i = Incident.builder()
                .title(r.title().trim())
                .message(blankToNull(r.message()))
                .severity(r.severity() == null ? "MAJOR" : r.severity())
                .status(r.status() == null ? "OPEN" : r.status())
                .createdBy(adminEmail)
                .build();
        if ("RESOLVED".equals(i.getStatus())) i.setResolvedAt(LocalDateTime.now());
        return view(incidents.save(i));
    }

    @Override
    @Transactional
    public IncidentResponse update(UUID id, IncidentRequest r) {
        Incident i = incidents.findById(id).orElseThrow(() -> new ResourceNotFoundException("Incident not found"));
        if (r.title() != null && !r.title().isBlank()) i.setTitle(r.title().trim());
        if (r.message() != null) i.setMessage(blankToNull(r.message()));
        if (r.severity() != null) i.setSeverity(r.severity());
        if (r.status() != null) {
            i.setStatus(r.status());
            i.setResolvedAt("RESOLVED".equals(r.status()) ? LocalDateTime.now() : null);
        }
        return view(incidents.save(i));
    }

    static IncidentResponse view(Incident i) {
        return new IncidentResponse(i.getId(), i.getTitle(), i.getMessage(), i.getSeverity(), i.getStatus(), i.getCreatedBy(), i.getCreatedAt(), i.getResolvedAt());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
