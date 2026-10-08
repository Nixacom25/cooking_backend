package com.cooked.backend.repository;

import com.cooked.backend.entity.SavedView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SavedViewRepository extends JpaRepository<SavedView, UUID> {

    List<SavedView> findByOwnerAndScreenOrderByNameAsc(String owner, String screen);

    long countByOwnerAndScreen(String owner, String screen);
}
