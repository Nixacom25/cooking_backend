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

@Data
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
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
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

    @Column(nullable = true, columnDefinition = "TEXT")
    private String category;

    @Column(nullable = true)
    private Integer servings;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String tips;

    @ElementCollection
    @CollectionTable(name = "recipe_steps", joinColumns = @JoinColumn(name = "recipe_id"))
    @Column(name = "step", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> steps = new ArrayList<>();

    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private boolean isPublic = false;

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<RecipeIngredient> recipeIngredients;

    @ManyToMany(mappedBy = "recipes")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<Cookbook> cookbooks;

    @Column(name = "source_url", nullable = true, columnDefinition = "TEXT")
    private String sourceUrl;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @ElementCollection
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
}
