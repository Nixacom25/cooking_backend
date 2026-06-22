package com.cooked.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePreferencesRequest {
    private List<String> dietaryPreferences;
    private List<String> allergies;
    private List<String> foodDislikes;
    private String groceryFrequency;
    private String groceryBudget;
    private List<String> groceryStores;
    private List<String> excitedFeatures;
    private java.util.Map<String, Integer> flavorDna;
    private String spiceLevel;
    private String cookingSkill;
    private String cookingTimePreference;
    private String cookingFrequency;
    private String cookingTarget;
    private List<String> favoriteCuisines;
    private List<String> kitchenAppliances;
    private List<String> notificationPreferences;
    private List<String> onboardingGoals;
    
    private List<String> frustrations;
    private String ageSelection;
    private String eatingOutSelection;
    private String grocerySelection;

    // Getters manuels pour garantir la compilation
    public List<String> getDietaryPreferences() { return dietaryPreferences; }
    public List<String> getAllergies() { return allergies; }
    public List<String> getFoodDislikes() { return foodDislikes; }
    public String getGroceryFrequency() { return groceryFrequency; }
    public String getGroceryBudget() { return groceryBudget; }
    public List<String> getGroceryStores() { return groceryStores; }
    public List<String> getExcitedFeatures() { return excitedFeatures; }
    public java.util.Map<String, Integer> getFlavorDna() { return flavorDna; }
    public String getSpiceLevel() { return spiceLevel; }
    public String getCookingSkill() { return cookingSkill; }
    public String getCookingTimePreference() { return cookingTimePreference; }
    public String getCookingFrequency() { return cookingFrequency; }
    public String getCookingTarget() { return cookingTarget; }
    public List<String> getFavoriteCuisines() { return favoriteCuisines; }
    public List<String> getKitchenAppliances() { return kitchenAppliances; }
    public List<String> getNotificationPreferences() { return notificationPreferences; }
    public List<String> getOnboardingGoals() { return onboardingGoals; }
    public List<String> getFrustrations() { return frustrations; }
    public String getAgeSelection() { return ageSelection; }
    public String getEatingOutSelection() { return eatingOutSelection; }
    public String getGrocerySelection() { return grocerySelection; }
}
