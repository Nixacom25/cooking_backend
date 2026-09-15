package com.cooked.backend.repository;

import com.cooked.backend.entity.RecipeImageMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RecipeImageMatchRepository extends JpaRepository<RecipeImageMatch, UUID> {
}
