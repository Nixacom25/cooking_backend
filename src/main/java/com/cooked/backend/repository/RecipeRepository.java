package com.cooked.backend.repository;

import com.cooked.backend.entity.Recipe;
import com.cooked.backend.entity.User;
import com.cooked.backend.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    List<Recipe> findByUser(User user);

    List<Recipe> findByCategory(Category category);

    List<Recipe> findByTitleContainingIgnoreCase(String title);
}
