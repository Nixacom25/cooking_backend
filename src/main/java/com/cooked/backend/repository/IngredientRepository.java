package com.cooked.backend.repository;

import com.cooked.backend.entity.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IngredientRepository extends JpaRepository<Ingredient, UUID> {
    Optional<Ingredient> findByName(String name);
    java.util.List<com.cooked.backend.entity.Ingredient> findByNameContainingIgnoreCase(String query);
}
