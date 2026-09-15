package com.cooked.backend.repository;

import com.cooked.backend.entity.ImageLibrary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ImageLibraryRepository extends JpaRepository<ImageLibrary, UUID> {
    boolean existsByImageUrl(String imageUrl);
    List<ImageLibrary> findByImageUrlIn(List<String> imageUrls);
}
