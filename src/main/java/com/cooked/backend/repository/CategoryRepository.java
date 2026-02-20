package com.cooked.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cooked.backend.entity.Category;

import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByName(String name);

    boolean existsByName(String name);
}