package com.cooked.backend.service;

import com.cooked.backend.dto.response.InstacartLinkResponse;
import com.cooked.backend.entity.GroceryItem;

import java.util.List;

public interface InstacartService {
    InstacartLinkResponse createShoppableList(List<GroceryItem> groceryItems);
}
