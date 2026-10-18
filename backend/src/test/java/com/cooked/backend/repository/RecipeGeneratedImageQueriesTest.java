package com.cooked.backend.repository;

import com.cooked.backend.entity.AiImageApprovalStatus;
import com.cooked.backend.entity.AiImageGenerationStatus;
import com.cooked.backend.entity.RecipeGeneratedImage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** AI recipe photo queries on a real (H2) database. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RecipeGeneratedImageQueriesTest {

    @Autowired private RecipeGeneratedImageRepository images;

    private RecipeGeneratedImage row(AiImageGenerationStatus status, AiImageApprovalStatus approval, boolean retried,
                                     String reason, Integer ms, double cost) {
        return images.save(RecipeGeneratedImage.builder()
                .recipeTitle("Chicken Tacos").recipeSnapshot("{}").imagePrompt("p")
                .imageProvider("openai").model("gpt-image-1")
                .generationStatus(status).approvalStatus(approval).retried(retried)
                .rejectionReason(reason).durationMs(ms).costUsd(cost)
                .generatedImageUrl("https://img/" + UUID.randomUUID())
                .build());
    }

    @Test
    void statsListingAndLinking() {
        RecipeGeneratedImage approved = row(AiImageGenerationStatus.SUCCEEDED, AiImageApprovalStatus.APPROVED, false, null, 20000, 0.04);
        approved.setImageLibraryId(UUID.randomUUID());
        images.save(approved);
        row(AiImageGenerationStatus.SUCCEEDED, AiImageApprovalStatus.REJECTED, true, "POOR_QUALITY", 40000, 0.08);
        row(AiImageGenerationStatus.FAILED, AiImageApprovalStatus.PENDING_REVIEW, true, null, null, 0.0);
        RecipeGeneratedImage pending = row(AiImageGenerationStatus.SUCCEEDED, AiImageApprovalStatus.PENDING_REVIEW, false, null, 30000, 0.04);
        images.flush();

        Object[] stats = images.statsByModel(LocalDateTime.now().minusDays(1)).get(0);
        assertEquals("gpt-image-1", stats[0]);
        assertEquals(4L, ((Number) stats[1]).longValue());   // started
        assertEquals(3L, ((Number) stats[2]).longValue());   // succeeded
        assertEquals(1L, ((Number) stats[3]).longValue());   // failed
        assertEquals(2L, ((Number) stats[4]).longValue());   // retried
        assertEquals(1L, ((Number) stats[5]).longValue());   // approved
        assertEquals(1L, ((Number) stats[6]).longValue());   // rejected
        assertEquals(0L, ((Number) stats[7]).longValue());   // regenerated
        assertEquals(1L, ((Number) stats[8]).longValue());   // added to library
        assertEquals(30000.0, ((Number) stats[9]).doubleValue(), 0.1);
        assertEquals(0.16, ((Number) stats[10]).doubleValue(), 0.0001);

        assertEquals("POOR_QUALITY", images.rejectionReasons(LocalDateTime.now().minusDays(1)).get(0)[0]);
        assertEquals(2, images.findByApprovalStatusOrderByCreatedAtDesc(AiImageApprovalStatus.PENDING_REVIEW, PageRequest.of(0, 10)).getTotalElements());
        assertEquals(1, images.findByGenerationStatusOrderByCreatedAtDesc(AiImageGenerationStatus.FAILED, PageRequest.of(0, 10)).getTotalElements());
        assertEquals(2, images.countByApprovalStatus(AiImageApprovalStatus.PENDING_REVIEW));
        assertTrue(images.findFirstByGeneratedImageUrlAndRecipeIdIsNull(pending.getGeneratedImageUrl()).isPresent());
        assertTrue(images.statsByModel(LocalDateTime.now().plusDays(1)).isEmpty());
    }
}
