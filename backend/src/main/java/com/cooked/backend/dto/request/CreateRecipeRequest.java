package com.cooked.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateRecipeRequest {
    @NotBlank(message = "Recipe name is required")
    private String name;

    private String image;
    private Integer cookTime;
    private Integer prepTime;
    private Integer kcal;
    private Double proteinG;
    private Double carbsG;
    private Double fatG;
    private Double fiberG;
    private Integer sodiumMg;
    private Integer servings;
    private String tips;
    private String cuisine;
    private List<String> categories;

    @Valid
    private List<IngredientPayload> ingredients;

    private List<String> steps;
    private List<String> equipment;
    private String sourceUrl;

    // Optional: cookbooks to attach to upon creation
    private List<UUID> cookbookIds;

    private String origin;

    private String imagePrompt;

    private Boolean status;

    // --- Getters & Setters Manuels ---
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getImagePrompt() { return imagePrompt; }
    public void setImagePrompt(String imagePrompt) { this.imagePrompt = imagePrompt; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public Integer getCookTime() { return cookTime; }
    public void setCookTime(Integer cookTime) { this.cookTime = cookTime; }
    public Integer getPrepTime() { return prepTime; }
    public void setPrepTime(Integer prepTime) { this.prepTime = prepTime; }
    public Integer getKcal() { return kcal; }
    public void setKcal(Integer kcal) { this.kcal = kcal; }
    public Double getProteinG() { return proteinG; }
    public void setProteinG(Double v) { this.proteinG = v; }
    public Double getCarbsG() { return carbsG; }
    public void setCarbsG(Double v) { this.carbsG = v; }
    public Double getFatG() { return fatG; }
    public void setFatG(Double v) { this.fatG = v; }
    public Double getFiberG() { return fiberG; }
    public void setFiberG(Double v) { this.fiberG = v; }
    public Integer getSodiumMg() { return sodiumMg; }
    public void setSodiumMg(Integer v) { this.sodiumMg = v; }
    public Integer getServings() { return servings; }
    public void setServings(Integer servings) { this.servings = servings; }
    public String getTips() { return tips; }
    public void setTips(String tips) { this.tips = tips; }
    public String getCuisine() { return cuisine; }
    public void setCuisine(String cuisine) { this.cuisine = cuisine; }
    public List<String> getCategories() { return categories; }
    public void setCategories(List<String> categories) { this.categories = categories; }
    public List<IngredientPayload> getIngredients() { return ingredients; }
    public void setIngredients(List<IngredientPayload> ingredients) { this.ingredients = ingredients; }
    public List<String> getSteps() { return steps; }
    public void setSteps(List<String> steps) { this.steps = steps; }
    public List<String> getEquipment() { return equipment; }
    public void setEquipment(List<String> equipment) { this.equipment = equipment; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public List<UUID> getCookbookIds() { return cookbookIds; }
    public void setCookbookIds(List<UUID> cookbookIds) { this.cookbookIds = cookbookIds; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public Boolean getStatus() { return status; }
    public void setStatus(Boolean status) { this.status = status; }
}
