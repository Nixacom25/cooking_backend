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
}
