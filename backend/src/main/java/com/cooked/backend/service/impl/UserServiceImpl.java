package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.CreateUserRequest;
import com.cooked.backend.dto.request.UpdatePasswordRequest;
import com.cooked.backend.dto.request.UpdateUserRequest;
import com.cooked.backend.dto.request.UpdateUserStatusRequest;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.dto.response.UserResponse;
import com.cooked.backend.entity.Role;
import com.cooked.backend.entity.Status;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.EmailAlreadyExistsException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.mapper.UserMapper;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.cooked.backend.service.CloudinaryService;
import com.cooked.backend.service.EmailService;

import java.util.UUID;
import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final CloudinaryService cloudinaryService;
    private final EmailService emailService;
    private final com.cooked.backend.service.UserInitializationService userInitializationService;
    private final com.cooked.backend.repository.RecipeRepository recipeRepository;
    private final com.cooked.backend.repository.MealPlanRepository mealPlanRepository;
    private final com.cooked.backend.repository.GroceryItemRepository groceryItemRepository;
    private final jakarta.persistence.EntityManager entityManager;

    @Override
    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
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
        user.setCookingTimePreference(request.getCookingTimePreference());
        user.setCookingFrequency(request.getCookingFrequency());
        user.setCookingTarget(request.getCookingTarget());
        user.setFavoriteCuisines(request.getFavoriteCuisines());
        user.setKitchenAppliances(request.getKitchenAppliances());
        user.setNotificationPreferences(request.getNotificationPreferences());
        user.setOnboardingGoals(request.getOnboardingGoals());
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
    public MessageResponse deleteClient(UUID id) {
        userRepository.deleteById(id);
        return new MessageResponse("Client deleted successfully");
    }

    @Override
    public Page<UserResponse> getAdmins(Pageable pageable) {
        return userRepository.findAllByRole(Role.ADMIN, pageable)
                .map(userMapper::toResponse);
    }

    @Override
    public UserResponse createAdmin(CreateUserRequest request) {
        return createUserWithRole(request, Role.ADMIN);
    }

    @Override
    public UserResponse updateAdmin(UUID id, UpdateUserRequest request) {
        return updateUser(id, request, Role.ADMIN);
    }

    @Override
    public MessageResponse deleteAdmin(UUID id) {
        userRepository.deleteById(id);
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
        
        // Handle recipes: delete duplicates, keep unique ones (nullify userId)
        if (user.getRecipes() != null && !user.getRecipes().isEmpty()) {
            // Create a copy of the list to avoid ConcurrentModificationException while modifying user.getRecipes()
            java.util.List<com.cooked.backend.entity.Recipe> userRecipes = new java.util.ArrayList<>(user.getRecipes());
            
            for (com.cooked.backend.entity.Recipe recipe : userRecipes) {
                // Check if this recipe has a duplicate (twin) in the database
                java.util.List<com.cooked.backend.entity.Recipe> sameNameRecipes = recipeRepository.findAllByNameIgnoreCase(recipe.getName());
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
                    
                    // Repoint/delete from favorite_recipes (native query to handle DB-level constraint safely)
                    try {
                        entityManager.createNativeQuery("UPDATE favorite_recipes SET recipe_id = :twinId WHERE recipe_id = :oldId")
                                .setParameter("twinId", twinRecipe.getId())
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
        
        return new MessageResponse("Your account and all associated data have been permanently deleted.");
    }
}
