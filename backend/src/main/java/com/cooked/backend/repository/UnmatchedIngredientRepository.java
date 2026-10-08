package com.cooked.backend.repository;

import com.cooked.backend.entity.UnmatchedIngredient;
import com.cooked.backend.entity.UnmatchedStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UnmatchedIngredientRepository extends JpaRepository<UnmatchedIngredient, UUID>, JpaSpecificationExecutor<UnmatchedIngredient> {

    Optional<UnmatchedIngredient> findByNameKey(String nameKey);

    List<UnmatchedIngredient> findByNameKeyInAndStatus(Collection<String> keys, UnmatchedStatus status);

    long countByStatus(UnmatchedStatus status);

    long countByStatusAndFirstSeenAtAfter(UnmatchedStatus status, LocalDateTime after);

    long countByStatusAndLastSeenAtAfter(UnmatchedStatus status, LocalDateTime after);

    @Query("select coalesce(sum(u.seenCount), 0) from UnmatchedIngredient u where u.status = :status and u.lastSeenAt >= :after")
    long sumSeen(@Param("status") UnmatchedStatus status, @Param("after") LocalDateTime after);
}
