package com.cooked.backend.repository;

import com.cooked.backend.entity.RecipeData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecipeDataRepository extends JpaRepository<RecipeData, Long> {
    boolean existsByName(String name);
    List<RecipeData> findAllByOrderByCreatedAtDesc();
}
