package com.cooked.backend.dto.response;

import com.cooked.backend.entity.Role;
import com.cooked.backend.entity.Status;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class UserResponse {
    private String id;
    private String email;
    private String firstname;
    private String lastname;
    private String phone;
    private String profilePictureUrl;
    private String discoverySource;
    private String otherDiscoverySource;
    private String language;
    private String country;
    private String measurementSystem;
    private List<String> dietaryPreferences;
    private List<String> allergies;
    private List<String> foodDislikes;
    private Map<String, Integer> flavorDna;
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
    private Role role;
    private Status status;
    private Integer onboardingRating;
    private String onboardingFeedback;
    private String subscriptionStatus;
    private String subscriptionType;
    private LocalDateTime subscriptionExpiresAt;
    private LocalDateTime createdAt;
    private boolean suggestionsReady;

    public UserResponse() {}

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFirstname() { return firstname; }
    public void setFirstname(String firstname) { this.firstname = firstname; }
    public String getLastname() { return lastname; }
    public void setLastname(String lastname) { this.lastname = lastname; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getProfilePictureUrl() { return profilePictureUrl; }
    public void setProfilePictureUrl(String profilePictureUrl) { this.profilePictureUrl = profilePictureUrl; }
    public String getDiscoverySource() { return discoverySource; }
    public void setDiscoverySource(String discoverySource) { this.discoverySource = discoverySource; }
    public String getOtherDiscoverySource() { return otherDiscoverySource; }
    public void setOtherDiscoverySource(String otherDiscoverySource) { this.otherDiscoverySource = otherDiscoverySource; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getMeasurementSystem() { return measurementSystem; }
    public void setMeasurementSystem(String measurementSystem) { this.measurementSystem = measurementSystem; }
    public List<String> getDietaryPreferences() { return dietaryPreferences; }
    public void setDietaryPreferences(List<String> dietaryPreferences) { this.dietaryPreferences = dietaryPreferences; }
    public List<String> getAllergies() { return allergies; }
    public void setAllergies(List<String> allergies) { this.allergies = allergies; }
    public List<String> getFoodDislikes() { return foodDislikes; }
    public void setFoodDislikes(List<String> foodDislikes) { this.foodDislikes = foodDislikes; }
    public Map<String, Integer> getFlavorDna() { return flavorDna; }
    public void setFlavorDna(Map<String, Integer> flavorDna) { this.flavorDna = flavorDna; }
    public String getSpiceLevel() { return spiceLevel; }
    public void setSpiceLevel(String spiceLevel) { this.spiceLevel = spiceLevel; }
    public String getCookingSkill() { return cookingSkill; }
    public void setCookingSkill(String cookingSkill) { this.cookingSkill = cookingSkill; }
    public String getCookingTimePreference() { return cookingTimePreference; }
    public void setCookingTimePreference(String cookingTimePreference) { this.cookingTimePreference = cookingTimePreference; }
    public String getCookingFrequency() { return cookingFrequency; }
    public void setCookingFrequency(String cookingFrequency) { this.cookingFrequency = cookingFrequency; }
    public String getCookingTarget() { return cookingTarget; }
    public void setCookingTarget(String cookingTarget) { this.cookingTarget = cookingTarget; }
    public List<String> getFavoriteCuisines() { return favoriteCuisines; }
    public void setFavoriteCuisines(List<String> favoriteCuisines) { this.favoriteCuisines = favoriteCuisines; }
    public List<String> getKitchenAppliances() { return kitchenAppliances; }
    public void setKitchenAppliances(List<String> kitchenAppliances) { this.kitchenAppliances = kitchenAppliances; }
    public String getMealPlanningStyle() { return mealPlanningStyle; }
    public void setMealPlanningStyle(String mealPlanningStyle) { this.mealPlanningStyle = mealPlanningStyle; }
    public List<String> getNotificationPreferences() { return notificationPreferences; }
    public void setNotificationPreferences(List<String> notificationPreferences) { this.notificationPreferences = notificationPreferences; }
    public List<String> getOnboardingGoals() { return onboardingGoals; }
    public void setOnboardingGoals(List<String> onboardingGoals) { this.onboardingGoals = onboardingGoals; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Integer getOnboardingRating() { return onboardingRating; }
    public void setOnboardingRating(Integer onboardingRating) { this.onboardingRating = onboardingRating; }
    public String getOnboardingFeedback() { return onboardingFeedback; }
    public void setOnboardingFeedback(String onboardingFeedback) { this.onboardingFeedback = onboardingFeedback; }
    public String getSubscriptionStatus() { return subscriptionStatus; }
    public void setSubscriptionStatus(String subscriptionStatus) { this.subscriptionStatus = subscriptionStatus; }
    public String getSubscriptionType() { return subscriptionType; }
    public void setSubscriptionType(String subscriptionType) { this.subscriptionType = subscriptionType; }
    public LocalDateTime getSubscriptionExpiresAt() { return subscriptionExpiresAt; }
    public void setSubscriptionExpiresAt(LocalDateTime subscriptionExpiresAt) { this.subscriptionExpiresAt = subscriptionExpiresAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public boolean isSuggestionsReady() { return suggestionsReady; }
    public void setSuggestionsReady(boolean suggestionsReady) { this.suggestionsReady = suggestionsReady; }
}
