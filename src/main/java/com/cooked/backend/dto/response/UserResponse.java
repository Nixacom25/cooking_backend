package com.cooked.backend.dto.response;

import com.cooked.backend.entity.Role;
import com.cooked.backend.entity.Status;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class UserResponse {
    private UUID id;
    private String firstname;
    private String lastname;
    private String phone;
    private String email;
    private String profilePictureUrl;
    private String discoverySource;
    private String otherDiscoverySource;
    private java.util.List<String> dietaryPreferences;
    private java.util.List<String> allergies;
    private java.util.List<String> foodDislikes;
    private java.util.Map<String, Integer> flavorDna;
    private String spiceLevel;
    private String cookingSkill;
    private String cookingTimePreference;
    private String cookingFrequency;
    private String cookingTarget;
    private java.util.List<String> favoriteCuisines;
    private java.util.List<String> kitchenAppliances;
    private String mealPlanningStyle;
    private java.util.List<String> notificationPreferences;
    private java.util.List<String> onboardingGoals;
    private Role role;
    private Status status;
    private Integer onboardingRating;
    private String onboardingFeedback;
    private LocalDateTime createdAt;
}