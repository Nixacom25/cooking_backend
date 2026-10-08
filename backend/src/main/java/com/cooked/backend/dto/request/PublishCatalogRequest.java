package com.cooked.backend.dto.request;

import jakarta.validation.constraints.Size;

public record PublishCatalogRequest(@Size(max = 500) String notes) {
}
