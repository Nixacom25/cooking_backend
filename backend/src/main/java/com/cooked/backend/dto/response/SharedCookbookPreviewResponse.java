package com.cooked.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** What the recipient sees before importing a shared cookbook. */
@Data
@Builder
public class SharedCookbookPreviewResponse {
    private UUID id;
    private String name;
    private String ownerName;
    private int recipeCount;
    /** Up to 4 recipe images for the cover. */
    private List<String> coverImages;
    /** The cookbook belongs to the viewer: nothing to import. */
    @JsonProperty("isOwner")
    private boolean isOwner;
}
