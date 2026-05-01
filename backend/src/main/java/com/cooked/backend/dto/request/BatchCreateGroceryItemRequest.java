package com.cooked.backend.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class BatchCreateGroceryItemRequest {
    private List<CreateGroceryItemRequest> items;
}
