package com.cooked.backend.repository;

import com.cooked.backend.entity.CategoryType;
import com.cooked.backend.entity.RecipeCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public interface RecipeCategoryRepository extends JpaRepository<RecipeCategory, UUID> {
    Optional<RecipeCategory> findByNameAndType(String name, CategoryType type);
    List<RecipeCategory> findByType(CategoryType type);

    @org.springframework.data.jpa.repository.Query("SELECT c, COUNT(r) FROM RecipeCategory c LEFT JOIN Recipe r ON (c.type = 'CATEGORY' AND r.category = c) OR (c.type = 'CUISINE' AND r.cuisine = c) GROUP BY c")
    List<Object[]> findAllWithRecipeCount();

    @org.springframework.data.jpa.repository.Query("SELECT c, COUNT(r) FROM RecipeCategory c LEFT JOIN Recipe r ON (c.type = 'CATEGORY' AND r.category = c) OR (c.type = 'CUISINE' AND r.cuisine = c) WHERE c.type = :type GROUP BY c")
    List<Object[]> findByTypeWithRecipeCount(@org.springframework.data.repository.query.Param("type") CategoryType type);
}
