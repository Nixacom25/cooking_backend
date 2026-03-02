package com.cooked.backend.service;

import com.cooked.backend.dto.request.CreateGroceryItemRequest;
import com.cooked.backend.dto.response.GroceryItemResponse;
import com.cooked.backend.dto.response.MessageResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface GroceryItemService {
    GroceryItemResponse create(String userEmail, CreateGroceryItemRequest request);

    List<GroceryItemResponse> getMyGroceryItems(String userEmail);

    List<GroceryItemResponse> getMyGroceryItemsByDate(String userEmail, LocalDate date);

    GroceryItemResponse toggleBought(UUID id, String userEmail);

    MessageResponse delete(UUID id, String userEmail);
}
