package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.SavedViewRequest;
import com.cooked.backend.entity.SavedView;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.AlertAckRepository;
import com.cooked.backend.repository.SavedViewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminPreferencesServiceImplTest {

    @Mock private AlertAckRepository acks;
    @Mock private SavedViewRepository views;
    private AdminPreferencesServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminPreferencesServiceImpl(acks, views, new ObjectMapper());
    }

    @Test
    void acknowledgeSkipsKnownAndBlankKeys() {
        when(acks.findExistingKeys(any())).thenReturn(List.of("ticket:1"));
        assertEquals(1, service.acknowledge(List.of("ticket:1", " ticket:2 ", "", "ticket:2"), "a@x.com"));
        verify(acks, times(1)).save(any());
    }

    @Test
    void viewsMustBeJsonObjectsAndOwnedToDelete() {
        assertThrows(BadRequestException.class, () -> service.saveView(new SavedViewRequest("users", "Trials", "[1,2]"), "a@x.com"));
        assertThrows(BadRequestException.class, () -> service.saveView(new SavedViewRequest("users", "Trials", "not json"), "a@x.com"));
        when(views.save(any())).thenAnswer(i -> i.getArgument(0));
        assertEquals("Trials", service.saveView(new SavedViewRequest("users", " Trials ", "{\"trial\":\"true\"}"), "a@x.com").name());

        UUID id = UUID.randomUUID();
        when(views.findById(id)).thenReturn(Optional.of(SavedView.builder().id(id).owner("other@x.com").build()));
        assertThrows(ResourceNotFoundException.class, () -> service.deleteView(id, "a@x.com"));
        verify(views, never()).delete(any());
    }
}
