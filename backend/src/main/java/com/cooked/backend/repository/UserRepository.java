package com.cooked.backend.repository;

import com.cooked.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByOriginalTransactionId(String originalTransactionId);
    Optional<User> findByIapReceiptData(String iapReceiptData);

    long countBySubscriptionStatus(com.cooked.backend.entity.SubscriptionStatus status);

    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE u.subscriptionStatus = :status AND u.createdAt <= :date")
    java.util.List<User> findUsersForDrip(@org.springframework.data.repository.query.Param("date") java.time.LocalDateTime date, @org.springframework.data.repository.query.Param("status") com.cooked.backend.entity.SubscriptionStatus status);

    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE u.subscriptionStatus = com.cooked.backend.entity.SubscriptionStatus.TRIAL "
            + "AND u.trialEndingReminderSent = false AND u.subscriptionExpiresAt BETWEEN :from AND :to")
    java.util.List<User> findUsersWithTrialEndingSoon(
            @org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from,
            @org.springframework.data.repository.query.Param("to") java.time.LocalDateTime to);

    Optional<User> findFirstByPhone(String phone);

    Optional<User> findByOtpCode(String otpCode);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    org.springframework.data.domain.Page<User> findAllByRole(com.cooked.backend.entity.Role role,
            org.springframework.data.domain.Pageable pageable);
            
    long countByRole(com.cooked.backend.entity.Role role);

    org.springframework.data.domain.Page<User> findAllByRoleIn(java.util.List<com.cooked.backend.entity.Role> roles,
            org.springframework.data.domain.Pageable pageable);
            
    long countByRoleIn(java.util.List<com.cooked.backend.entity.Role> roles);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT r.user FROM Recipe r WHERE r.isPublic = true")
    java.util.List<User> findPublicCreators();

    // Notification campaign targeting methods
    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE u.id IN :userIds")
    java.util.List<User> findAllByUserIds(@org.springframework.data.repository.query.Param("userIds") java.util.List<UUID> userIds);
    
    java.util.List<User> findBySubscriptionStatusNotNull();
    
    java.util.List<User> findBySubscriptionStatusNull();
    
    java.util.List<User> findBySubscriptionStatus(String status);
    
    java.util.List<User> findByLastActiveAfter(LocalDateTime date);
    
    java.util.List<User> findByLastActiveBefore(LocalDateTime date);
    
    java.util.List<User> findByCreatedAtAfter(LocalDateTime date);
    
    java.util.List<User> findByEmailIn(java.util.List<String> emails);
}
