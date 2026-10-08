package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.SavedViewRequest;
import com.cooked.backend.dto.response.SavedViewResponse;
import com.cooked.backend.entity.AlertAck;
import com.cooked.backend.entity.SavedView;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.AlertAckRepository;
import com.cooked.backend.repository.SavedViewRepository;
import com.cooked.backend.service.AdminPreferencesService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminPreferencesServiceImpl implements AdminPreferencesService {

    static final int MAX_VIEWS_PER_SCREEN = 30;

    private final AlertAckRepository acks;
    private final SavedViewRepository views;
    private final ObjectMapper json;

    @Override
    @Transactional(readOnly = true)
    public List<String> acknowledgedAlerts() {
        return acks.findAllKeys();
    }

    @Override
    @Transactional
    public int acknowledge(Collection<String> keys, String adminEmail) {
        Set<String> wanted = new LinkedHashSet<>();
        keys.stream().filter(Objects::nonNull).map(String::trim).filter(k -> !k.isEmpty()).forEach(wanted::add);
        if (wanted.isEmpty()) return 0;
        wanted.removeAll(acks.findExistingKeys(wanted));
        wanted.forEach(k -> acks.save(AlertAck.builder().alertKey(k).ackBy(adminEmail).build()));
        return wanted.size();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SavedViewResponse> views(String adminEmail, String screen) {
        return views.findByOwnerAndScreenOrderByNameAsc(adminEmail, screen).stream().map(AdminPreferencesServiceImpl::view).toList();
    }

    @Override
    @Transactional
    public SavedViewResponse saveView(SavedViewRequest r, String adminEmail) {
        try {
            JsonNode node = json.readTree(r.query());
            if (node == null || !node.isObject()) throw new BadRequestException("query must be a JSON object");
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BadRequestException("query must be a JSON object");
        }
        if (views.countByOwnerAndScreen(adminEmail, r.screen()) >= MAX_VIEWS_PER_SCREEN) {
            throw new BadRequestException("You already have " + MAX_VIEWS_PER_SCREEN + " views on this screen.");
        }
        return view(views.save(SavedView.builder().owner(adminEmail).screen(r.screen()).name(r.name().trim()).query(r.query()).build()));
    }

    @Override
    @Transactional
    public void deleteView(UUID id, String adminEmail) {
        SavedView v = views.findById(id).orElseThrow(() -> new ResourceNotFoundException("View not found"));
        if (!v.getOwner().equalsIgnoreCase(adminEmail)) throw new ResourceNotFoundException("View not found");
        views.delete(v);
    }

    static SavedViewResponse view(SavedView v) {
        return new SavedViewResponse(v.getId(), v.getScreen(), v.getName(), v.getQuery(), v.getCreatedAt());
    }
}
