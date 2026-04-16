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
@Table(name = "recipes", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "user_id", "name" })
})
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    @Column(nullable = false)
    private String name;

    @Column(nullable = true)
    private String image;

    @Column(nullable = true)
    private Integer cookTime; // In minutes

    @Column(nullable = true)
    private Integer kcal;

    @Column(nullable = true)
    private String category;

    @Column(nullable = true)
    private Integer servings;

    @Column(nullable = true, length = 2000)
    private String tips;

    @ElementCollection
    @CollectionTable(name = "recipe_steps", joinColumns = @JoinColumn(name = "recipe_id"))
    @Column(name = "step", length = 2000)
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

    @Column(name = "source_url", nullable = true, length = 1000)
    private String sourceUrl;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
