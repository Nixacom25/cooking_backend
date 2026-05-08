package com.cooked.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "users")
@SQLDelete(sql = "UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Column(nullable = false)
    private String firstname;

    @NotBlank
    @Column(nullable = false)
    private String lastname;

    @Column(unique = false, nullable = true)
    private String phone;

    @NotBlank
    @Email
    @Column(unique = true, nullable = false)
    private String email;

    @NotBlank
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.FREE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private SubscriptionType subscriptionType = SubscriptionType.NONE;

    private LocalDateTime subscriptionExpiresAt;
    private String originalTransactionId;

    @ElementCollection
    @CollectionTable(name = "user_dietary_preferences", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "preference")
    @Builder.Default
    private List<String> dietaryPreferences = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "user_allergies", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "allergy")
    @Builder.Default
    private List<String> allergies = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "user_food_dislikes", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "dislike")
    @Builder.Default
    private List<String> foodDislikes = new ArrayList<>();

    private String photo;
    private String discoverySource;
    private String otherDiscoverySource;
    private String language;
    private String country;
    private String alternativeRegion;
    private String measurementSystem;

    @ElementCollection
    @CollectionTable(name = "user_flavor_dna", joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyColumn(name = "flavor_key")
    @Column(name = "flavor_value")
    @Builder.Default
    private java.util.Map<String, Integer> flavorDna = new java.util.HashMap<>();

    private String spiceLevel;
    private String cookingSkill;
    private String cookingTimePreference;
    private String cookingFrequency;
    private String cookingTarget;

    @ElementCollection
    @CollectionTable(name = "user_favorite_cuisines", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "cuisine")
    @Builder.Default
    private List<String> favoriteCuisines = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "user_kitchen_appliances", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "appliance")
    @Builder.Default
    private List<String> kitchenAppliances = new ArrayList<>();

    private String mealPlanningStyle;

    @ElementCollection
    @CollectionTable(name = "user_notification_preferences", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "preference")
    @Builder.Default
    private List<String> notificationPreferences = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "user_onboarding_goals", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "goal")
    @Builder.Default
    private List<String> onboardingGoals = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Provider provider = Provider.LOCAL;

    private String providerId;
    private String otpCode;
    private LocalDateTime otpExpiration;

    @Column(nullable = false)
    @Builder.Default
    private Integer resendCount = 0;

    private LocalDateTime lockoutUntil;
    private Integer onboardingRating;

    @Column(columnDefinition = "TEXT")
    private String onboardingFeedback;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    // --- Getters & Setters Manuels ---
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getFirstname() { return firstname; }
    public void setFirstname(String firstname) { this.firstname = firstname; }
    public String getLastname() { return lastname; }
    public void setLastname(String lastname) { this.lastname = lastname; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPhoto() { return photo; }
    public void setPhoto(String photo) { this.photo = photo; }
    public String getDiscoverySource() { return discoverySource; }
    public void setDiscoverySource(String discoverySource) { this.discoverySource = discoverySource; }
    public String getOtherDiscoverySource() { return otherDiscoverySource; }
    public void setOtherDiscoverySource(String otherDiscoverySource) { this.otherDiscoverySource = otherDiscoverySource; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getAlternativeRegion() { return alternativeRegion; }
    public void setAlternativeRegion(String alternativeRegion) { this.alternativeRegion = alternativeRegion; }
    public String getMeasurementSystem() { return measurementSystem; }
    public void setMeasurementSystem(String measurementSystem) { this.measurementSystem = measurementSystem; }
    
    public List<String> getDietaryPreferences() { return dietaryPreferences; }
    public void setDietaryPreferences(List<String> dietaryPreferences) { this.dietaryPreferences = dietaryPreferences; }
    public List<String> getAllergies() { return allergies; }
    public void setAllergies(List<String> allergies) { this.allergies = allergies; }
    public List<String> getFoodDislikes() { return foodDislikes; }
    public void setFoodDislikes(List<String> foodDislikes) { this.foodDislikes = foodDislikes; }
    public java.util.Map<String, Integer> getFlavorDna() { return flavorDna; }
    public void setFlavorDna(java.util.Map<String, Integer> flavorDna) { this.flavorDna = flavorDna; }
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
    public Integer getOnboardingRating() { return onboardingRating; }
    public void setOnboardingRating(Integer onboardingRating) { this.onboardingRating = onboardingRating; }
    public String getOnboardingFeedback() { return onboardingFeedback; }
    public void setOnboardingFeedback(String onboardingFeedback) { this.onboardingFeedback = onboardingFeedback; }

    public String getOtpCode() { return otpCode; }
    public void setOtpCode(String otpCode) { this.otpCode = otpCode; }
    public LocalDateTime getOtpExpiration() { return otpExpiration; }
    public void setOtpExpiration(LocalDateTime otpExpiration) { this.otpExpiration = otpExpiration; }
    public LocalDateTime getLockoutUntil() { return lockoutUntil; }
    public void setLockoutUntil(LocalDateTime lockoutUntil) { this.lockoutUntil = lockoutUntil; }
    public Integer getResendCount() { return resendCount; }
    public void setResendCount(Integer resendCount) { this.resendCount = resendCount; }

    public Provider getProvider() { return provider; }
    public void setProvider(Provider provider) { this.provider = provider; }
    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }

    public SubscriptionStatus getSubscriptionStatus() { return subscriptionStatus; }
    public void setSubscriptionStatus(SubscriptionStatus subscriptionStatus) { this.subscriptionStatus = subscriptionStatus; }
    public SubscriptionType getSubscriptionType() { return subscriptionType; }
    public void setSubscriptionType(SubscriptionType subscriptionType) { this.subscriptionType = subscriptionType; }
    public LocalDateTime getSubscriptionExpiresAt() { return subscriptionExpiresAt; }
    public void setSubscriptionExpiresAt(LocalDateTime subscriptionExpiresAt) { this.subscriptionExpiresAt = subscriptionExpiresAt; }
    public String getOriginalTransactionId() { return originalTransactionId; }
    public void setOriginalTransactionId(String originalTransactionId) { this.originalTransactionId = originalTransactionId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() { return email; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() {
        return status != Status.BLOCKED && status != Status.ARCHIVED;
    }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() {
        return status == Status.ACTIVE || status == Status.PENDING_VERIFICATION;
    }
}