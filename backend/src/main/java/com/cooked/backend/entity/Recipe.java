package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "recipes")
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private User user;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String name;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String image;

    @Column(nullable = true)
    private Integer cookTime; // In minutes

    @Column(nullable = true)
    private Integer prepTime; // In minutes

    @Column(nullable = true)
    private Integer kcal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = true)
    private RecipeCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuisine_id", nullable = true)
    private RecipeCategory cuisine;

    @Column(nullable = true)
    private Integer servings;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String tips;

    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @CollectionTable(name = "recipe_steps", joinColumns = @JoinColumn(name = "recipe_id"))
    @Column(name = "step", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> steps = new ArrayList<>();

    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private boolean isPublic = false;

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<RecipeIngredient> recipeIngredients;

    @ManyToMany(mappedBy = "recipes")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Set<Cookbook> cookbooks;

    @Column(name = "source_url", nullable = true, columnDefinition = "TEXT")
    private String sourceUrl;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @CollectionTable(name = "recipe_equipment", joinColumns = @JoinColumn(name = "recipe_id"))
    @Column(name = "item", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> equipment = new ArrayList<>();

    @Column(name = "expires_at", nullable = true)
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false)
    @Builder.Default
    private RecipeOrigin origin = RecipeOrigin.MANUAL;

    @Column(name = "is_pinned", nullable = false)
    @Builder.Default
    private boolean isPinned = false;

    // --- Getters & Setters Manuels ---
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public Integer getCookTime() { return cookTime; }
    public void setCookTime(Integer cookTime) { this.cookTime = cookTime; }
    public Integer getPrepTime() { return prepTime; }
    public void setPrepTime(Integer prepTime) { this.prepTime = prepTime; }
    public Integer getKcal() { return kcal; }
    public void setKcal(Integer kcal) { this.kcal = kcal; }
    public RecipeCategory getCategory() { return category; }
    public void setCategory(RecipeCategory category) { this.category = category; }
    public RecipeCategory getCuisine() { return cuisine; }
    public void setCuisine(RecipeCategory cuisine) { this.cuisine = cuisine; }
    public Integer getServings() { return servings; }
    public void setServings(Integer servings) { this.servings = servings; }
    public String getTips() { return tips; }
    public void setTips(String tips) { this.tips = tips; }
    public List<String> getSteps() { return steps; }
    public void setSteps(List<String> steps) { this.steps = steps; }
    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }
    public Set<RecipeIngredient> getRecipeIngredients() { return recipeIngredients; }
    public void setRecipeIngredients(Set<RecipeIngredient> recipeIngredients) { this.recipeIngredients = recipeIngredients; }
    public Set<Cookbook> getCookbooks() { return cookbooks; }
    public void setCookbooks(Set<Cookbook> cookbooks) { this.cookbooks = cookbooks; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public List<String> getEquipment() { return equipment; }
    public void setEquipment(List<String> equipment) { this.equipment = equipment; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public RecipeOrigin getOrigin() { return origin; }
    public void setOrigin(RecipeOrigin origin) { this.origin = origin; }
    public boolean isPinned() { return isPinned; }
    public void setPinned(boolean pinned) { isPinned = pinned; }
}
