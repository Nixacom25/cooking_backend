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

@Getter
@Setter
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

    @Column(unique = true, nullable = true)
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

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != Status.BLOCKED && status != Status.ARCHIVED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == Status.ACTIVE || status == Status.PENDING_VERIFICATION;
    }
}