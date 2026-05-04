package com.cooked.backend.dto.response;

import com.cooked.backend.dto.request.CreateRecipeRequest;
import com.cooked.backend.dto.request.IngredientPayload;
import java.util.List;

public class ScanResponse {
    private boolean success;
    private String user_id;
    private List<IngredientPayload> allowed_ingredients;
    private List<AiIngredientDetectionResponse.RestrictedIngredient> restricted_ingredients;
    private String image_url;
    private List<CreateRecipeRequest> recipes;

    public ScanResponse() {}

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getUser_id() { return user_id; }
    public void setUser_id(String user_id) { this.user_id = user_id; }
    public List<IngredientPayload> getAllowed_ingredients() { return allowed_ingredients; }
    public void setAllowed_ingredients(List<IngredientPayload> allowed_ingredients) { this.allowed_ingredients = allowed_ingredients; }
    public List<AiIngredientDetectionResponse.RestrictedIngredient> getRestricted_ingredients() { return restricted_ingredients; }
    public void setRestricted_ingredients(List<AiIngredientDetectionResponse.RestrictedIngredient> restricted_ingredients) { this.restricted_ingredients = restricted_ingredients; }
    public String getImage_url() { return image_url; }
    public void setImage_url(String image_url) { this.image_url = image_url; }
    public List<CreateRecipeRequest> getRecipes() { return recipes; }
    public void setRecipes(List<CreateRecipeRequest> recipes) { this.recipes = recipes; }

    public static ScanResponseBuilder builder() {
        return new ScanResponseBuilder();
    }

    public static class ScanResponseBuilder {
        private final ScanResponse response = new ScanResponse();

        public ScanResponseBuilder success(boolean success) { response.setSuccess(success); return this; }
        public ScanResponseBuilder user_id(String user_id) { response.setUser_id(user_id); return this; }
        public ScanResponseBuilder allowed_ingredients(List<IngredientPayload> allowed_ingredients) { response.setAllowed_ingredients(allowed_ingredients); return this; }
        public ScanResponseBuilder restricted_ingredients(List<AiIngredientDetectionResponse.RestrictedIngredient> restricted_ingredients) { response.setRestricted_ingredients(restricted_ingredients); return this; }
        public ScanResponseBuilder image_url(String image_url) { response.setImage_url(image_url); return this; }
        public ScanResponseBuilder recipes(List<CreateRecipeRequest> recipes) { response.setRecipes(recipes); return this; }

        public ScanResponse build() {
            return response;
        }
    }
}
