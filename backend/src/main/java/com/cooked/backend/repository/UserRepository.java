package com.cooked.backend.repository;

import com.cooked.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    /** Distinct onboarding discovery sources (admin Users filter options). */
    @org.springframework.data.jpa.repository.Query("select distinct trim(u.discoverySource) from User u where u.discoverySource is not null and trim(u.discoverySource) <> '' order by trim(u.discoverySource)")
    List<String> findDistinctDiscoverySources();
    Optional<User> findByEmail(String email);
    Optional<User> findFirstByRevenueCatCustomerId(String revenueCatCustomerId);
    Optional<User> findByOriginalTransactionId(String originalTransactionId);
    Optional<User> findByIapReceiptData(String iapReceiptData);

    long countBySubscriptionStatus(com.cooked.backend.entity.SubscriptionStatus status);

    long countByRoleAndSubscriptionStatusIn(com.cooked.backend.entity.Role role,
            java.util.Collection<com.cooked.backend.entity.SubscriptionStatus> statuses);

    long countByRoleAndSubscriptionStatusInAndSubscriptionType(com.cooked.backend.entity.Role role,
            java.util.Collection<com.cooked.backend.entity.SubscriptionStatus> statuses,
            com.cooked.backend.entity.SubscriptionType type);

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

    java.util.List<User> findAllByRole(com.cooked.backend.entity.Role role);

    /** Clients still on the free plan who signed up in [from, to) — one day's cohort. */
    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE u.role = com.cooked.backend.entity.Role.CLIENT "
            + "AND u.subscriptionStatus = :status AND u.createdAt >= :from AND u.createdAt < :to")
    java.util.List<User> findDripCohort(@org.springframework.data.repository.query.Param("status") com.cooked.backend.entity.SubscriptionStatus status,
                                        @org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from,
                                        @org.springframework.data.repository.query.Param("to") java.time.LocalDateTime to);

    @org.springframework.data.jpa.repository.Query("select u.email from User u where u.role = :role and u.email is not null")
    java.util.List<String> findEmailsByRole(@org.springframework.data.repository.query.Param("role") com.cooked.backend.entity.Role role);

    org.springframework.data.domain.Page<User> findAllByRoleIn(java.util.List<com.cooked.backend.entity.Role> roles,
            org.springframework.data.domain.Pageable pageable);

    long countByRoleIn(java.util.List<com.cooked.backend.entity.Role> roles);

    org.springframework.data.domain.Page<User> findAllByRoleAndSubscriptionStatusIn(
            com.cooked.backend.entity.Role role,
            java.util.List<com.cooked.backend.entity.SubscriptionStatus> statuses,
            org.springframework.data.domain.Pageable pageable);

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

    // --- Admin analytics: sign-ups (acquisition screen).
    interface DayCount {
        java.time.LocalDate getDay();
        Long getTotal();
    }

    interface LabelCount {
        String getLabel();
        Long getTotal();
    }

    @org.springframework.data.jpa.repository.Query("select cast(u.createdAt as LocalDate) as day, count(u) as total from User u "
            + "where u.role = :role and u.createdAt >= :from group by cast(u.createdAt as LocalDate)")
    java.util.List<DayCount> countSignupsByDay(
            @org.springframework.data.repository.query.Param("role") com.cooked.backend.entity.Role role,
            @org.springframework.data.repository.query.Param("from") LocalDateTime from);

    @org.springframework.data.jpa.repository.Query("select count(u) from User u where u.role = :role and u.createdAt >= :from and u.createdAt < :to")
    long countSignupsBetween(
            @org.springframework.data.repository.query.Param("role") com.cooked.backend.entity.Role role,
            @org.springframework.data.repository.query.Param("from") LocalDateTime from,
            @org.springframework.data.repository.query.Param("to") LocalDateTime to);

    /** "How did you hear about Cooked?" answers of users who signed up since [from]. */
    @org.springframework.data.jpa.repository.Query("select u.discoverySource as label, count(u) as total from User u "
            + "where u.role = :role and u.createdAt >= :from group by u.discoverySource order by count(u) desc")
    java.util.List<LabelCount> countSignupsBySource(
            @org.springframework.data.repository.query.Param("role") com.cooked.backend.entity.Role role,
            @org.springframework.data.repository.query.Param("from") LocalDateTime from);
}
