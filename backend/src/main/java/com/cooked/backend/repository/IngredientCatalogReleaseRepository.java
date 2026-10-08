package com.cooked.backend.repository;

import com.cooked.backend.entity.IngredientCatalogRelease;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IngredientCatalogReleaseRepository extends JpaRepository<IngredientCatalogRelease, UUID> {

    Optional<IngredientCatalogRelease> findTopByOrderByVersionDesc();

    List<IngredientCatalogRelease> findTop20ByOrderByVersionDesc();

    /** Clears the manifest of old releases; only the recent ones stay downloadable. */
    @Modifying
    @Query("update IngredientCatalogRelease r set r.manifest = null where r.version <= :version and r.manifest is not null")
    int clearManifestsUpTo(@Param("version") int version);
}
