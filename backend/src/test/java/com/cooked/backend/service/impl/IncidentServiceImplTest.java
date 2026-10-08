package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.IncidentRequest;
import com.cooked.backend.entity.Incident;
import com.cooked.backend.repository.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentServiceImplTest {

    @Mock private IncidentRepository repo;
    @InjectMocks private IncidentServiceImpl service;

    @Test
    void declareDefaultsAndResolve() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        var created = service.declare(new IncidentRequest(" Scans failing ", "", null, null), "boss@cooked.app");
        assertEquals("Scans failing", created.title());
        assertEquals("MAJOR", created.severity());
        assertEquals("OPEN", created.status());
        assertNull(created.message());

        UUID id = UUID.randomUUID();
        Incident i = Incident.builder().id(id).title("x").severity("MAJOR").status("OPEN").build();
        when(repo.findById(id)).thenReturn(Optional.of(i));
        var resolved = service.update(id, new IncidentRequest(null, null, null, "RESOLVED"));
        assertEquals("RESOLVED", resolved.status());
        assertNotNull(resolved.resolvedAt());
    }
}
