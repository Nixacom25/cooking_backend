package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.CreateCookbookRequest;
import com.cooked.backend.dto.response.CookbookResponse;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.entity.Cookbook;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.CookbookRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.CookbookService;
import com.cooked.backend.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CookbookServiceImpl implements CookbookService {

    private final CookbookRepository cookbookRepository;
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;

    @Override
    public CookbookResponse create(String userEmail, CreateCookbookRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (cookbookRepository.existsByUserIdAndName(user.getId(), request.getName())) {
            throw new BadRequestException("You already have a cookbook named '" + request.getName() + "'.");
        }

        Cookbook cb = Cookbook.builder()
                .user(user)
                .name(request.getName())
                // recipes will be initialized when recipes are added
                .build();

        Cookbook savedCb = cookbookRepository.save(cb);

        activityLogService.logActivity(user, "Cookbook Created",
                "Successfully created cookbook named '" + request.getName() + "'.");

        return mapToResponse(savedCb);
    }

    @Override
    public List<CookbookResponse> getMyCookbooks(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return cookbookRepository.findAllByUserId(user.getId()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public CookbookResponse getCookbook(UUID id, String userEmail) {
        Cookbook cookbook = cookbookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cookbook not found"));

        if (!cookbook.getUser().getEmail().equals(userEmail)) {
            throw new BadRequestException("You do not have permission to view this cookbook.");
        }

        return mapToResponse(cookbook);
    }

    @Override
    public MessageResponse delete(UUID id, String userEmail) {
        Cookbook cookbook = cookbookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cookbook not found"));

        if (!cookbook.getUser().getEmail().equals(userEmail)) {
            throw new BadRequestException("You do not have permission to delete this cookbook.");
        }

        cookbookRepository.delete(cookbook);
        return new MessageResponse("Cookbook deleted successfully.");
    }

    private CookbookResponse mapToResponse(Cookbook cookbook) {
        return CookbookResponse.builder()
                .id(cookbook.getId())
                .name(cookbook.getName())
                .recipes(Collections.emptyList()) // Keep it light or populate it
                .createdAt(cookbook.getCreatedAt())
                .updatedAt(cookbook.getUpdatedAt())
                .build();
    }
}
