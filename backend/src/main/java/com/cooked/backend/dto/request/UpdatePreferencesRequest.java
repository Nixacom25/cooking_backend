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
    private String language;
    private String country;
    private String alternativeRegion;
    private String measurementSystem;
    private java.util.Map<String, Integer> flavorDna;
    private String spiceLevel;
    private String cookingSkill;
    private String cookingTimePreference;
    private String cookingFrequency;
    private String cookingTarget;
    private List<String> favoriteCuisines;
    private List<String> kitchenAppliances;
    private String mealPlanningStyle;
    private List<String> notificationPreferences;
    private List<String> onboardingGoals;
    private Integer onboardingRating;
    private String onboardingFeedback;

    // Getters manuels pour garantir la compilation
    public List<String> getDietaryPreferences() { return dietaryPreferences; }
    public List<String> getAllergies() { return allergies; }
    public List<String> getFoodDislikes() { return foodDislikes; }
    public String getLanguage() { return language; }
    public String getCountry() { return country; }
    public String getAlternativeRegion() { return alternativeRegion; }
    public String getMeasurementSystem() { return measurementSystem; }
    public java.util.Map<String, Integer> getFlavorDna() { return flavorDna; }
    public String getSpiceLevel() { return spiceLevel; }
    public String getCookingSkill() { return cookingSkill; }
    public String getCookingTimePreference() { return cookingTimePreference; }
    public String getCookingFrequency() { return cookingFrequency; }
    public String getCookingTarget() { return cookingTarget; }
    public List<String> getFavoriteCuisines() { return favoriteCuisines; }
    public List<String> getKitchenAppliances() { return kitchenAppliances; }
    public String getMealPlanningStyle() { return mealPlanningStyle; }
    public List<String> getNotificationPreferences() { return notificationPreferences; }
    public List<String> getOnboardingGoals() { return onboardingGoals; }
    public Integer getOnboardingRating() { return onboardingRating; }
    public String getOnboardingFeedback() { return onboardingFeedback; }
}
