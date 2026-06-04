package com.cooked.backend.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class RecipeResponse {
    private UUID id;
    private String name;
    private String image;
    private Integer cookTime;
    private Integer prepTime;
    private Integer kcal;
    private String category;
    private String cuisine;
    private Integer servings;
    private String tips;
    private RecipeCreatorResponse creator;
    private List<RecipeIngredientResponse> ingredients;
    private List<String> steps;
    private List<String> equipment;
    private boolean isPublic;
    private boolean isFavorite;
    private String sourceUrl;
    private String shareUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean isSuggested;
    private LocalDateTime expiresAt;
    private String origin;
    private boolean isInCookbook;
    private boolean isPinned;
    private Double totalPrice;
    private Integer ingredientsCount;
    private Boolean status; // true if modified today

    // Constructeur vide
    public RecipeResponse() {}

    // Getters & Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public Integer getCookTime() { return cookTime; }
    public void setCookTime(Integer cookTime) { this.cookTime = cookTime; }
    public Integer getPrepTime() { return prepTime; }
    public void setPrepTime(Integer prepTime) { this.prepTime = prepTime; }
    public Integer getKcal() { return kcal; }
    public void setKcal(Integer kcal) { this.kcal = kcal; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getCuisine() { return cuisine; }
    public void setCuisine(String cuisine) { this.cuisine = cuisine; }
    public Integer getServings() { return servings; }
    public void setServings(Integer servings) { this.servings = servings; }
    public String getTips() { return tips; }
    public void setTips(String tips) { this.tips = tips; }
    public RecipeCreatorResponse getCreator() { return creator; }
    public void setCreator(RecipeCreatorResponse creator) { this.creator = creator; }
    public List<RecipeIngredientResponse> getIngredients() { return ingredients; }
    public void setIngredients(List<RecipeIngredientResponse> ingredients) { this.ingredients = ingredients; }
    public List<String> getSteps() { return steps; }
    public void setSteps(List<String> steps) { this.steps = steps; }
    public List<String> getEquipment() { return equipment; }
    public void setEquipment(List<String> equipment) { this.equipment = equipment; }
    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }
    public boolean isFavorite() { return isFavorite; }
    public void setFavorite(boolean isFavorite) { this.isFavorite = isFavorite; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getShareUrl() { return shareUrl; }
    public void setShareUrl(String shareUrl) { this.shareUrl = shareUrl; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public boolean isSuggested() { return isSuggested; }
    public void setSuggested(boolean isSuggested) { this.isSuggested = isSuggested; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public boolean isInCookbook() { return isInCookbook; }
    public void setInCookbook(boolean isInCookbook) { this.isInCookbook = isInCookbook; }
    public boolean isPinned() { return isPinned; }
    public void setPinned(boolean pinned) { isPinned = pinned; }
    public Double getTotalPrice() { return totalPrice; }
    public void setTotalPrice(Double totalPrice) { this.totalPrice = totalPrice; }
    public Integer getIngredientsCount() { return ingredientsCount; }
    public void setIngredientsCount(Integer ingredientsCount) { this.ingredientsCount = ingredientsCount; }
    public Boolean getStatus() { return status; }
    public void setStatus(Boolean status) { this.status = status; }

    // Builder manuel
    public static RecipeResponseBuilder builder() {
        return new RecipeResponseBuilder();
    }

    public static class RecipeResponseBuilder {
        private final RecipeResponse response = new RecipeResponse();

        public RecipeResponseBuilder id(UUID id) { response.setId(id); return this; }
        public RecipeResponseBuilder name(String name) { response.setName(name); return this; }
        public RecipeResponseBuilder image(String image) { response.setImage(image); return this; }
        public RecipeResponseBuilder cookTime(Integer cookTime) { response.setCookTime(cookTime); return this; }
        public RecipeResponseBuilder prepTime(Integer prepTime) { response.setPrepTime(prepTime); return this; }
        public RecipeResponseBuilder kcal(Integer kcal) { response.setKcal(kcal); return this; }
        public RecipeResponseBuilder category(String category) { response.setCategory(category); return this; }
        public RecipeResponseBuilder cuisine(String cuisine) { response.setCuisine(cuisine); return this; }
        public RecipeResponseBuilder servings(Integer servings) { response.setServings(servings); return this; }
        public RecipeResponseBuilder tips(String tips) { response.setTips(tips); return this; }
        public RecipeResponseBuilder creator(RecipeCreatorResponse creator) { response.setCreator(creator); return this; }
        public RecipeResponseBuilder ingredients(List<RecipeIngredientResponse> ingredients) { response.setIngredients(ingredients); return this; }
        public RecipeResponseBuilder steps(List<String> steps) { response.setSteps(steps); return this; }
        public RecipeResponseBuilder equipment(List<String> equipment) { response.setEquipment(equipment); return this; }
        public RecipeResponseBuilder isPublic(boolean isPublic) { response.setPublic(isPublic); return this; }
        public RecipeResponseBuilder isFavorite(boolean isFavorite) { response.setFavorite(isFavorite); return this; }
        public RecipeResponseBuilder sourceUrl(String sourceUrl) { response.setSourceUrl(sourceUrl); return this; }
        public RecipeResponseBuilder shareUrl(String shareUrl) { response.setShareUrl(shareUrl); return this; }
        public RecipeResponseBuilder createdAt(LocalDateTime createdAt) { response.setCreatedAt(createdAt); return this; }
        public RecipeResponseBuilder updatedAt(LocalDateTime updatedAt) { response.setUpdatedAt(updatedAt); return this; }
        public RecipeResponseBuilder isSuggested(boolean isSuggested) { response.setSuggested(isSuggested); return this; }
        public RecipeResponseBuilder expiresAt(LocalDateTime expiresAt) { response.setExpiresAt(expiresAt); return this; }
        public RecipeResponseBuilder origin(String origin) { response.setOrigin(origin); return this; }
        public RecipeResponseBuilder isInCookbook(boolean isInCookbook) { response.setInCookbook(isInCookbook); return this; }
        public RecipeResponseBuilder isPinned(boolean isPinned) { response.setPinned(isPinned); return this; }
        public RecipeResponseBuilder totalPrice(Double totalPrice) { response.setTotalPrice(totalPrice); return this; }
        public RecipeResponseBuilder ingredientsCount(Integer ingredientsCount) { response.setIngredientsCount(ingredientsCount); return this; }
        public RecipeResponseBuilder status(Boolean status) { response.setStatus(status); return this; }

        public RecipeResponse build() {
            return response;
        }
    }
}
