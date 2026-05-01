package com.cooked.backend.repository;

import com.cooked.backend.entity.UserSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, UUID> {
    Optional<UserSubscription> findByUserId(UUID userId);

    List<UserSubscription> findAllByEndDateBeforeAndStatusNot(LocalDateTime endDate,
            com.cooked.backend.entity.SubscriptionStatus status);
}
