package com.cooked.backend.dto.response;

import com.cooked.backend.dto.request.IngredientPayload;
import java.util.List;

public class AiIngredientDetectionResponse {
    private boolean success;
    private List<IngredientPayload> allowed_ingredients;
    private List<RestrictedIngredient> restricted_ingredients;
    private String image_url;
    private Double confidence;

    public AiIngredientDetectionResponse() {}

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public List<IngredientPayload> getAllowed_ingredients() { return allowed_ingredients; }
    public void setAllowed_ingredients(List<IngredientPayload> allowed_ingredients) { this.allowed_ingredients = allowed_ingredients; }
    public List<RestrictedIngredient> getRestricted_ingredients() { return restricted_ingredients; }
    public void setRestricted_ingredients(List<RestrictedIngredient> restricted_ingredients) { this.restricted_ingredients = restricted_ingredients; }
    public String getImage_url() { return image_url; }
    public void setImage_url(String image_url) { this.image_url = image_url; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }

    public static class RestrictedIngredient {
        private String name;
        private String icon;
        private String reason;

        public RestrictedIngredient() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getIcon() { return icon; }
        public void setIcon(String icon) { this.icon = icon; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
