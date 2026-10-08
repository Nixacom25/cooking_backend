package com.cooked.backend.repository;

import com.cooked.backend.entity.IngredientVisual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IngredientVisualRepository extends JpaRepository<IngredientVisual, UUID>, JpaSpecificationExecutor<IngredientVisual> {

    Optional<IngredientVisual> findByCanonicalId(String canonicalId);

    boolean existsByCanonicalId(String canonicalId);

    /** Visuals answering to any of these normalized keys, by name, canonical id or alias. */
    @Query("select distinct v from IngredientVisual v left join v.aliases a "
            + "where v.nameKey in :keys or v.canonicalId in :keys or a.aliasKey in :keys")
    List<IngredientVisual> findByAnyKey(@Param("keys") Collection<String> keys);

    /** Enabled visuals as rows [id, nameKey, canonicalId, name] (used to suggest a match for unknown names). */
    @Query("select v.id, v.nameKey, v.canonicalId, v.name from IngredientVisual v where v.enabled = true")
    List<Object[]> nameKeys();

    /** Aliases of enabled visuals as rows [visualId, aliasKey]. */
    @Query("select v.id, a.aliasKey from IngredientVisual v join v.aliases a where v.enabled = true")
    List<Object[]> aliasKeys();

    @Query("select count(a) from IngredientVisual v join v.aliases a")
    long countAliases();

    @Query("select distinct v.category from IngredientVisual v order by v.category")
    List<String> categories();

    @Query("select distinct v.collection from IngredientVisual v where v.collection is not null order by v.collection")
    List<String> collections();

    long countByUpdatedAtAfter(LocalDateTime after);

    @Query("select v from IngredientVisual v where v.enabled = true and v.svg is not null order by v.canonicalId")
    List<IngredientVisual> findPublishable();
}
