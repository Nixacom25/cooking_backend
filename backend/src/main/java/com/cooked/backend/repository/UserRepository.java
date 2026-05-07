package com.cooked.backend.repository;

import com.cooked.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    long countBySubscriptionStatus(com.cooked.backend.entity.SubscriptionStatus status);

    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE u.subscriptionStatus = :status AND u.createdAt <= :date")
    java.util.List<User> findUsersForDrip(@org.springframework.data.repository.query.Param("date") java.time.LocalDateTime date, @org.springframework.data.repository.query.Param("status") com.cooked.backend.entity.SubscriptionStatus status);

    Optional<User> findFirstByPhone(String phone);

    Optional<User> findByOtpCode(String otpCode);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    org.springframework.data.domain.Page<User> findAllByRole(com.cooked.backend.entity.Role role,
            org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT r.user FROM Recipe r WHERE r.isPublic = true")
    java.util.List<User> findPublicCreators();
}
