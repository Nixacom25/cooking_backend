package com.cooked.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {

    @Parameter(description = "User's first name")
    private String firstname;

    @Parameter(description = "User's last name")
    private String lastname;

    @Parameter(description = "User's phone number")
    private String phone;

    @Parameter(description = "User's email address")
    @Email
    @NotBlank
    private String email;

    @Parameter(description = "User's password")
    private String password;

    @Parameter(description = "Profile photo URL")
    private String photo;

    @Parameter(description = "Provider (LOCAL, GOOGLE, APPLE)")
    private String provider; // e.g. "LOCAL", "GOOGLE", "APPLE"

    @Parameter(description = "Discovery source (social, friend, etc.)")
    private String discoverySource;

    @Parameter(description = "Other discovery source details")
    private String otherDiscoverySource;

    private String groceryFrequency;
    private String groceryBudget;
    private java.util.List<String> groceryStores;
    private java.util.List<String> excitedFeatures;

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
    private java.util.List<String> notificationPreferences;
    private java.util.List<String> onboardingGoals;
    
    private java.util.List<String> frustrations;
    private String ageSelection;
    private String eatingOutSelection;
    private String grocerySelection;
}
