package com.cooked.backend.repository;

import com.cooked.backend.entity.AiImageApprovalStatus;
import com.cooked.backend.entity.AiImageGenerationStatus;
import com.cooked.backend.entity.RecipeGeneratedImage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecipeGeneratedImageRepository extends JpaRepository<RecipeGeneratedImage, UUID> {

    Page<RecipeGeneratedImage> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<RecipeGeneratedImage> findByApprovalStatusOrderByCreatedAtDesc(AiImageApprovalStatus status, Pageable pageable);

    Page<RecipeGeneratedImage> findByGenerationStatusOrderByCreatedAtDesc(AiImageGenerationStatus status, Pageable pageable);

    Optional<RecipeGeneratedImage> findFirstByGeneratedImageUrlAndRecipeIdIsNull(String generatedImageUrl);

    /**
     * Per model since [since]: {model, total, succeeded, failed, retried, approved,
     * rejected, regenerated, addedToLibrary, avgDurationMs (succeeded), costUsd}.
     */
    @Query("SELECT i.model, COUNT(i), "
            + "SUM(CASE WHEN i.generationStatus = com.cooked.backend.entity.AiImageGenerationStatus.SUCCEEDED THEN 1 ELSE 0 END), "
            + "SUM(CASE WHEN i.generationStatus = com.cooked.backend.entity.AiImageGenerationStatus.FAILED THEN 1 ELSE 0 END), "
            + "SUM(CASE WHEN i.retried = true THEN 1 ELSE 0 END), "
            + "SUM(CASE WHEN i.approvalStatus = com.cooked.backend.entity.AiImageApprovalStatus.APPROVED THEN 1 ELSE 0 END), "
            + "SUM(CASE WHEN i.approvalStatus = com.cooked.backend.entity.AiImageApprovalStatus.REJECTED THEN 1 ELSE 0 END), "
            + "SUM(CASE WHEN i.regeneratedFromId IS NOT NULL THEN 1 ELSE 0 END), "
            + "SUM(CASE WHEN i.imageLibraryId IS NOT NULL THEN 1 ELSE 0 END), "
            + "AVG(CASE WHEN i.generationStatus = com.cooked.backend.entity.AiImageGenerationStatus.SUCCEEDED THEN i.durationMs ELSE NULL END), "
            + "COALESCE(SUM(i.costUsd), 0) "
            + "FROM RecipeGeneratedImage i WHERE i.createdAt >= :since GROUP BY i.model")
    List<Object[]> statsByModel(@Param("since") LocalDateTime since);

    @Query("SELECT i.rejectionReason, COUNT(i) FROM RecipeGeneratedImage i "
            + "WHERE i.createdAt >= :since AND i.approvalStatus = com.cooked.backend.entity.AiImageApprovalStatus.REJECTED "
            + "GROUP BY i.rejectionReason")
    List<Object[]> rejectionReasons(@Param("since") LocalDateTime since);

    long countByApprovalStatus(AiImageApprovalStatus status);
}
