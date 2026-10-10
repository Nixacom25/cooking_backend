package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.CreateUserRequest;
import com.cooked.backend.dto.request.UpdatePasswordRequest;
import com.cooked.backend.dto.request.UpdateUserRequest;
import com.cooked.backend.dto.request.UpdateUserStatusRequest;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.dto.response.UserResponse;
import com.cooked.backend.entity.Role;
import com.cooked.backend.entity.Status;
import com.cooked.backend.entity.SubscriptionStatus;
import com.cooked.backend.entity.SubscriptionType;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.EmailAlreadyExistsException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.mapper.UserMapper;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.cooked.backend.service.CloudinaryService;
import com.cooked.backend.service.EmailService;

import com.cooked.backend.util.StoreDates;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.io.IOException;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    private final com.cooked.backend.repository.AdminInsightsRepository insightsRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final CloudinaryService cloudinaryService;
    private final EmailService emailService;
    private final com.cooked.backend.service.UserInitializationService userInitializationService;
    private final com.cooked.backend.repository.RecipeRepository recipeRepository;
    private final com.cooked.backend.repository.MealPlanRepository mealPlanRepository;
    private final com.cooked.backend.repository.GroceryItemRepository groceryItemRepository;
    private final com.cooked.backend.repository.CookbookRepository cookbookRepository;
    private final com.cooked.backend.repository.DeviceSessionRepository deviceSessionRepository;
    private final com.cooked.backend.repository.RecipeAssignmentRepository recipeAssignmentRepository;
    private final jakarta.persistence.EntityManager entityManager;
    private final com.cooked.backend.repository.UserSubscriptionRepository userSubscriptionRepository;
    private final com.cooked.backend.service.UserActivityRecorder activityRecorder;
    private final com.cooked.backend.service.SubscriptionVerificationService subscriptionVerificationService;

    @Override
    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // No subscription check here: a signed-in user whose subscription
        // lapsed must still load their profile so the app can route them
        // and show the paywall. Premium actions are gated by
        // SubscriptionRequiredFilter (which also lets /user/me through).
        return userMapper.toResponse(user);
    }

    @Override
    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return userMapper.toResponse(user);
    }

    @Override
    public UserResponse updateCurrentUser(String email, UpdateUserRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setFirstname(request.getFirstname());
        user.setLastname(request.getLastname());
        user.setPhone(request.getPhone());
        user.setDiscoverySource(request.getDiscoverySource());
        user.setOtherDiscoverySource(request.getOtherDiscoverySource());

        userRepository.save(user);

        emailService.sendAccountUpdateEmail(email, "Your profile information has been updated successfully.");

        return userMapper.toResponse(user);
    }

    @Override
    public MessageResponse updatePreferences(String email,
            com.cooked.backend.dto.request.UpdatePreferencesRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setDietaryPreferences(request.getDietaryPreferences());
        user.setAllergies(request.getAllergies());
        user.setFoodDislikes(request.getFoodDislikes());

        user.setGroceryFrequency(request.getGroceryFrequency());
        user.setGroceryBudget(request.getGroceryBudget());
        user.setGroceryStores(request.getGroceryStores());
        user.setExcitedFeatures(request.getExcitedFeatures());
        user.setFlavorDna(request.getFlavorDna());
        user.setSpiceLevel(request.getSpiceLevel());
        user.setCookingSkill(request.getCookingSkill());
        // Optional: older app versions don't send it, keep the stored value.
        if (request.getLanguage() != null && !request.getLanguage().isBlank()) {
            user.setLanguage(request.getLanguage().trim());
        }
        user.setCookingTimePreference(request.getCookingTimePreference());
        user.setCookingFrequency(request.getCookingFrequency());
        user.setCookingTarget(request.getCookingTarget());
        user.setFavoriteCuisines(request.getFavoriteCuisines());
        user.setKitchenAppliances(request.getKitchenAppliances());
        user.setNotificationPreferences(request.getNotificationPreferences());
        user.setOnboardingGoals(request.getOnboardingGoals());
        user.setFrustrations(request.getFrustrations());
        user.setAgeSelection(request.getAgeSelection());
        user.setEatingOutSelection(request.getEatingOutSelection());
        user.setGrocerySelection(request.getGrocerySelection());
        User savedUser = userRepository.save(user);

        // Trigger initialization asynchronously if not already done
        // This handles cases where preferences are set AFTER registration
        userInitializationService.initializeAccount(savedUser.getId());

        return new MessageResponse("Preferences updated successfully");
    }

    @Override
    public MessageResponse updatePassword(String email, UpdatePasswordRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BadRequestException("Incorrect old password");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        emailService.sendAccountUpdateEmail(email, "Your account password has been changed successfully.");

        return new MessageResponse("Password updated successfully");
    }

    @Override
    public UserResponse updateNotificationPreferences(String email,
            com.cooked.backend.dto.request.UpdateNotificationPreferencesRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (request.getPushEnabled() != null) {
            user.setPushEnabled(request.getPushEnabled());
        }
        if (request.getPushRemindersEnabled() != null) {
            user.setPushRemindersEnabled(request.getPushRemindersEnabled());
        }
        if (request.getPushNewsOffersEnabled() != null) {
            user.setPushNewsOffersEnabled(request.getPushNewsOffersEnabled());
        }

        userRepository.save(user);

        return userMapper.toResponse(user);
    }

    @Override
    public Page<UserResponse> getClients(Pageable pageable) {
        return userRepository.findAllByRole(Role.CLIENT, pageable)
                .map(userMapper::toResponse);
    }

    @Override
    public UserResponse createClient(CreateUserRequest request) {
        return createUserWithRole(request, Role.CLIENT);
    }

    @Override
    public UserResponse updateClient(UUID id, UpdateUserRequest request) {
        return updateUser(id, request, Role.CLIENT);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public MessageResponse deleteClient(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        cleanupAndExecuteDelete(user);
        return new MessageResponse("Client deleted successfully");
    }

    @Override
    public Page<UserResponse> getCreators(Pageable pageable) {
        return userRepository.findAllByRole(Role.CREATOR, pageable)
                .map(userMapper::toResponse);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public MessageResponse updateUserRole(UUID id, String role) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        try {
            Role newRole = Role.valueOf(role.toUpperCase());
            user.setRole(newRole);
            
            // If role is CREATOR, set subscription to INFINITE and update UserSubscription
            if (newRole == Role.CREATOR) {
                user.setSubscriptionStatus(SubscriptionStatus.INFINITE);
                
                // Create or update UserSubscription entity (source of truth)
                com.cooked.backend.entity.UserSubscription subscription = userSubscriptionRepository.findByUserId(user.getId())
                        .orElseGet(() -> {
                            com.cooked.backend.entity.UserSubscription newSub = new com.cooked.backend.entity.UserSubscription();
                            newSub.setUser(user);
                            newSub.setStartDate(LocalDateTime.now());
                            return newSub;
                        });
                
                subscription.setStatus(SubscriptionStatus.INFINITE);
                // Set a far future date for INFINITE subscription
                subscription.setEndDate(LocalDateTime.now().plusYears(100));
                subscription.setIsYearly(false);
                
                userSubscriptionRepository.save(subscription);
            }
            
            userRepository.save(user);
            return new MessageResponse("User role updated successfully");
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid role: " + role);
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public MessageResponse updateUserSubscription(UUID id, String subscriptionStatus) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        try {
            SubscriptionStatus newStatus = SubscriptionStatus.valueOf(subscriptionStatus.toUpperCase());
            user.setSubscriptionStatus(newStatus);
            
            // If subscription is INFINITE, ensure role is CREATOR
            if (newStatus == SubscriptionStatus.INFINITE && user.getRole() != Role.CREATOR) {
                user.setRole(Role.CREATOR);
            }
            
            userRepository.save(user);
            return new MessageResponse("User subscription updated successfully");
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid subscription status: " + subscriptionStatus);
        }
    }

    @Override
    public Page<UserResponse> getAdmins(Pageable pageable) {
        return userRepository.findAllByRoleIn(java.util.Arrays.asList(Role.ADMIN, Role.EDITOR), pageable)
                .map(userMapper::toResponse);
    }

    @Override
    public Page<UserResponse> getEditors(Pageable pageable) {
        return userRepository.findAllByRole(Role.EDITOR, pageable)
                .map(userMapper::toResponse);
    }

    @Override
    public UserResponse createAdmin(CreateUserRequest request) {
        Role targetRole = request.getRole();
        if (targetRole == null || (targetRole != Role.ADMIN && targetRole != Role.EDITOR)) {
            targetRole = Role.ADMIN;
        }
        return createUserWithRole(request, targetRole);
    }

    @Override
    public UserResponse updateAdmin(UUID id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getRole() != Role.ADMIN && user.getRole() != Role.EDITOR) {
            throw new BadRequestException("User is not an admin or editor");
        }

        user.setFirstname(request.getFirstname());
        user.setLastname(request.getLastname());
        user.setPhone(request.getPhone());
        user.setDiscoverySource(request.getDiscoverySource());
        user.setOtherDiscoverySource(request.getOtherDiscoverySource());

        if (request.getRole() != null && (request.getRole() == Role.ADMIN || request.getRole() == Role.EDITOR)) {
            user.setRole(request.getRole());
        }

        userRepository.save(user);

        return userMapper.toResponse(user);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public MessageResponse deleteAdmin(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        cleanupAndExecuteDelete(user);
        return new MessageResponse("Admin deleted successfully");
    }

    @Override
    public MessageResponse updateUserStatus(UUID id, UpdateUserStatusRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setStatus(request.getStatus());
        userRepository.save(user);

        return new MessageResponse("User status updated successfully");
    }

    private UserResponse createUserWithRole(CreateUserRequest request, Role role) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException("Email already exists");
        }

        User user = User.builder()
                .firstname(request.getFirstname())
                .lastname(request.getLastname())
                .phone(request.getPhone())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .status(Status.ACTIVE)
                .build();

        userRepository.save(user);
        return userMapper.toResponse(user);
    }

    private UserResponse updateUser(UUID id, UpdateUserRequest request, Role expectedRole) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getRole() != expectedRole) {
            throw new BadRequestException("User is not a " + expectedRole.name());
        }

        user.setFirstname(request.getFirstname());
        user.setLastname(request.getLastname());
        user.setPhone(request.getPhone());
        user.setDiscoverySource(request.getDiscoverySource());
        user.setOtherDiscoverySource(request.getOtherDiscoverySource());

        userRepository.save(user);

        return userMapper.toResponse(user);
    }

    @Override
    public MessageResponse uploadProfilePhoto(String email, MultipartFile file) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please select a file to upload");
        }

        try {
            String photoUrl = cloudinaryService.upload(file);
            user.setPhoto(photoUrl);
            userRepository.save(user);

            emailService.sendAccountUpdateEmail(email, "Your profile photo has been updated successfully.");

            return new MessageResponse("Profile photo updated successfully");
        } catch (IOException e) {
            throw new RuntimeException("Could not store file to Cloudinary", e);
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public MessageResponse deleteCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String firstname = user.getFirstname();
        cleanupAndExecuteDelete(user);
        emailService.sendAccountDeletedEmail(email, firstname);
        return new MessageResponse("Your account and all associated data have been permanently deleted.");
    }

    @Override
    public MessageResponse updateFcmToken(String email, String fcmToken) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setFcmToken(fcmToken);
        userRepository.save(user);
        return new MessageResponse("Push notification token registered successfully.");
    }

    @Override
    public void updateLastActive(String email) {
        updateLastActive(email, null, null);
    }

    @Override
    public void updateLastActive(String email, String appVersion, String country) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setLastActive(LocalDateTime.now());
        String version = ClientContext.version(appVersion);
        if (version != null) user.setAppVersion(version);
        String iso = ClientContext.country(country);
        if (iso != null) user.setCountry(iso);
        userRepository.save(user);
        try {
            activityRecorder.recordToday(user.getId());
        } catch (RuntimeException e) {
            // Analytics only: a lost day (e.g. concurrent duplicate) must not fail the ping.
            log.debug("Activity day not recorded for {}: {}", user.getId(), e.getMessage());
        }
    }

    @Override
    public void syncSubscription(String email, Map<String, Object> subscriptionData) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Boolean isActive = (Boolean) subscriptionData.get("isActive");
        String expirationDateStr = (String) subscriptionData.get("expirationDate");
        String productId = (String) subscriptionData.get("productId");
        String revenueCatCustomerId = (String) subscriptionData.get("revenueCatCustomerId");

        // Update RevenueCat customer ID
        Object priceLabel = subscriptionData.get("priceLabel");
        if (priceLabel instanceof String label && !label.isBlank() && label.length() <= 40) {
            user.setPlanPriceLabel(label.trim());
        }

        if (revenueCatCustomerId != null && !revenueCatCustomerId.isEmpty()) {
            user.setRevenueCatCustomerId(revenueCatCustomerId);
        }

        if (subscriptionVerificationService.isAvailable()) {
            // Never trust isActive / expirationDate sent by the app: anyone
            // can POST them. Re-read the entitlement from RevenueCat instead.
            // A fresh purchase (app says active, we don't) is re-checked
            // almost immediately; other syncs are throttled.
            boolean claimsActive = Boolean.TRUE.equals(isActive);
            Duration minInterval = claimsActive && !isPremium(user) ? Duration.ofSeconds(5) : Duration.ofSeconds(60);
            boolean verified = subscriptionVerificationService.refreshFromStore(user, minInterval);
            if (claimsActive && !verified && !isPremium(user)) {
                log.warn("Sync for {} claims an active subscription RevenueCat doesn't confirm", email);
            }
            userRepository.save(user);
            log.info("Synced subscription data for user: {} (verified with RevenueCat: {})", email, verified);
            return;
        }

        // Fallback when REVENUECAT_SECRET_API_KEY isn't set: client-reported data.
        log.warn("RevenueCat secret key not configured - trusting app-reported subscription for {}", email);
        if (isActive != null && isActive) {
            user.setSubscriptionStatus(SubscriptionStatus.ACTIVE);

            // Set expiration date if available (ISO instant, e.g. "...Z")
            LocalDateTime expirationDate = StoreDates.parse(expirationDateStr);
            if (expirationDate != null) {
                user.setSubscriptionExpiresAt(expirationDate);
            } else if (expirationDateStr != null && !expirationDateStr.isEmpty()) {
                log.warn("Failed to parse expiration date: {}", expirationDateStr);
            }

            // Set subscription type based on product ID
            if (productId != null) {
                if (productId.toLowerCase().contains("year")) {
                    user.setSubscriptionType(SubscriptionType.YEARLY);
                } else if (productId.toLowerCase().contains("month")) {
                    user.setSubscriptionType(SubscriptionType.MONTHLY);
                }
            }
        } else {
            // Check if subscription is expired
            LocalDateTime expirationDate = StoreDates.parse(expirationDateStr);
            if (expirationDate != null && expirationDate.isBefore(LocalDateTime.now())
                    && user.getSubscriptionStatus() != SubscriptionStatus.INFINITE) {
                user.setSubscriptionStatus(SubscriptionStatus.EXPIRED);
            }
        }

        userRepository.save(user);
        log.info("Synced subscription data for user: {}", email);
    }

    /** Same rule as SubscriptionRequiredFilter: an access-granting status not yet expired. */
    private static boolean isPremium(User user) {
        SubscriptionStatus status = user.getSubscriptionStatus();
        if (status == SubscriptionStatus.INFINITE) return true;
        if (status != SubscriptionStatus.ACTIVE && status != SubscriptionStatus.TRIAL
                && status != SubscriptionStatus.PREMIUM) return false;
        return user.getSubscriptionExpiresAt() == null || user.getSubscriptionExpiresAt().isAfter(LocalDateTime.now());
    }

    @Override
    public MessageResponse sendWelcomeEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        // Only send welcome email if it hasn't been sent yet
        if (user.isWelcomeEmailSent()) {
            return new MessageResponse("Welcome email already sent");
        }
        
        emailService.sendWelcomeEmail(user.getEmail(), user.getFirstname());
        user.setWelcomeEmailSent(true);
        userRepository.save(user);
        return new MessageResponse("Welcome email sent successfully");
    }

    private void cleanupAndExecuteDelete(User user) {
        // Delete any recipe assignments where this user is assignedToUser or assignedByUser
        recipeAssignmentRepository.deleteByUserId(user.getId());

        // Handle recipes: delete duplicates, keep unique ones (nullify userId)
        if (user.getRecipes() != null && !user.getRecipes().isEmpty()) {
            // Create a copy of the list to avoid ConcurrentModificationException while modifying user.getRecipes()
            java.util.List<com.cooked.backend.entity.Recipe> userRecipes = new java.util.ArrayList<>(user.getRecipes());

            // Fetch every possible "twin" (same name, any user) in a single query
            // instead of one query per recipe - accounts with many recipes were
            // making this loop do dozens/hundreds of round trips and time out.
            java.util.Set<String> lowerNames = new java.util.HashSet<>();
            for (com.cooked.backend.entity.Recipe recipe : userRecipes) {
                if (recipe.getName() != null) {
                    lowerNames.add(recipe.getName().toLowerCase());
                }
            }
            java.util.Map<String, java.util.List<com.cooked.backend.entity.Recipe>> sameNameByLowerName = new java.util.HashMap<>();
            if (!lowerNames.isEmpty()) {
                for (com.cooked.backend.entity.Recipe candidate : recipeRepository.findAllByNameIgnoreCaseIn(lowerNames)) {
                    if (candidate.getName() == null) continue;
                    sameNameByLowerName
                        .computeIfAbsent(candidate.getName().toLowerCase(), k -> new java.util.ArrayList<>())
                        .add(candidate);
                }
            }

            for (com.cooked.backend.entity.Recipe recipe : userRecipes) {
                // Check if this recipe has a duplicate (twin) in the database
                java.util.List<com.cooked.backend.entity.Recipe> sameNameRecipes = recipe.getName() != null
                    ? sameNameByLowerName.getOrDefault(recipe.getName().toLowerCase(), java.util.List.of())
                    : java.util.List.of();
                boolean hasTwin = false;
                com.cooked.backend.entity.Recipe twinRecipe = null;

                for (com.cooked.backend.entity.Recipe other : sameNameRecipes) {
                    if (!other.getId().equals(recipe.getId())) {
                        // Check if the other recipe does not belong to the user being deleted
                        if (other.getUser() == null || !other.getUser().getId().equals(user.getId())) {
                            hasTwin = true;
                            twinRecipe = other;
                            break;
                        }
                    }
                }

                if (hasTwin) {
                    // This recipe has a twin in the database.
                    // We delete it completely, but first repoint references from other users to the twin.
                    
                    // Repoint other users' Meal Plans to the twin
                    mealPlanRepository.repointRecipe(recipe, twinRecipe);
                    
                    // Repoint other users' Grocery Items to the twin
                    groceryItemRepository.repointRecipe(recipe, twinRecipe);
                    
                    // Repoint Recipe Assignments to the twin
                    recipeAssignmentRepository.repointRecipe(recipe.getId(), twinRecipe);
                    
                    // Delete any remaining assignments for this recipe ID
                    recipeAssignmentRepository.deleteByRecipeId(recipe.getId());
                    
                    // DELETE from cookbook_recipes explicitly since Cookbook is the owning side
                    try {
                        entityManager.createNativeQuery("DELETE FROM cookbook_recipes WHERE recipe_id = :oldId")
                                .setParameter("oldId", recipe.getId())
                                .executeUpdate();
                    } catch (Exception e) {
                        // Ignore if table or columns don't exist
                    }
                    
                    // Remove recipe from cookbooks it belongs to
                    if (recipe.getCookbooks() != null) {
                        for (com.cooked.backend.entity.Cookbook cb : recipe.getCookbooks()) {
                            cb.getRecipes().remove(recipe);
                        }
                        recipe.getCookbooks().clear();
                    }
                    
                    // Remove recipe from the user's collection to avoid Hibernate issues
                    user.getRecipes().remove(recipe);
                    
                    // Delete the recipe
                    recipeRepository.delete(recipe);
                } else {
                    // This recipe is unique (no duplicate in the database).
                    // We keep it, just set user to null
                    recipe.setUser(null);
                    if (recipe.getOrigin() == com.cooked.backend.entity.RecipeOrigin.SCAN || 
                        recipe.getOrigin() == com.cooked.backend.entity.RecipeOrigin.IMPORT) {
                        recipe.setOrigin(com.cooked.backend.entity.RecipeOrigin.SUGGESTED);
                    }
                    recipeRepository.save(recipe);
                }
            }
            // Clear the list to ensure they are not part of the user object anymore
            user.getRecipes().clear();
        }

        userRepository.delete(user);
    }

    @Override
    public com.cooked.backend.dto.response.UserStatsResponse getUserStats(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        long totalRecipes = recipeRepository.countByUserId(id);
        long totalCookbooks = cookbookRepository.countByUserId(id);
        long totalScans = recipeRepository.countByUserIdAndOrigin(id, com.cooked.backend.entity.RecipeOrigin.SCAN);
        long totalImports = recipeRepository.countByUserIdAndOrigin(id, com.cooked.backend.entity.RecipeOrigin.IMPORT);
        long savedExploreRecipes = recipeRepository.countByUserIdAndOrigin(id, com.cooked.backend.entity.RecipeOrigin.EXPLORE);
        
        long totalSessions = deviceSessionRepository.countByUser(user);
        
        java.util.List<com.cooked.backend.entity.DeviceSession> sessions = deviceSessionRepository.findByUserOrderByLastActiveDesc(user);
        java.time.LocalDateTime lastLogin = sessions.isEmpty() ? user.getUpdatedAt() : sessions.get(0).getLastActive();

        return com.cooked.backend.dto.response.UserStatsResponse.builder()
                .accountCreatedAt(user.getCreatedAt())
                .lastLogin(lastLogin)
                .totalSessions(totalSessions)
                .totalRecipes(totalRecipes)
                .totalCookbooks(totalCookbooks)
                .totalScans(totalScans)
                .totalImports(totalImports)
                .savedExploreRecipes(savedExploreRecipes)
                .groceryAdds(insightsRepository.groceryAddsOf(id))
                .webSearches(insightsRepository.searchesOf(id))
                .build();
    }
}
