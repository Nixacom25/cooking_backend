package com.cooked.backend.repository;

import com.cooked.backend.entity.RevenueCatPendingEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RevenueCatPendingEventRepository extends JpaRepository<RevenueCatPendingEvent, UUID> {

    boolean existsByEventId(String eventId);

    /** Unresolved events mentioning this app user id. */
    @Query("select e from RevenueCatPendingEvent e where e.resolvedAt is null and e.appUserIds like concat('%,', :id, ',%') order by e.createdAt")
    List<RevenueCatPendingEvent> findOpenFor(@Param("id") String appUserId);

    long countByResolvedAtIsNull();
}
