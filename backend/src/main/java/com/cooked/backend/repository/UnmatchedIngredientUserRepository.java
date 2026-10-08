package com.cooked.backend.repository;

import com.cooked.backend.entity.UnmatchedIngredientUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UnmatchedIngredientUserRepository extends JpaRepository<UnmatchedIngredientUser, UUID> {

    boolean existsByUnmatchedIdAndUserEmail(UUID unmatchedId, String userEmail);

    int countByUnmatchedId(UUID unmatchedId);
}
