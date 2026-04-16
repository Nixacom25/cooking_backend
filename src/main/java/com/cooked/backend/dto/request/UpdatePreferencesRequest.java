package com.cooked.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
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
}
