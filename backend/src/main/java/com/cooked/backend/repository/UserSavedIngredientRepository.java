package com.cooked.backend.repository;

import com.cooked.backend.entity.User;
import com.cooked.backend.entity.UserSavedIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserSavedIngredientRepository extends JpaRepository<UserSavedIngredient, UUID> {
    List<UserSavedIngredient> findAllByUserOrderByCreatedAtDesc(User user);
    void deleteByUserAndName(User user, String name);
}
