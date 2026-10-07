package com.cooked.backend.repository;

import com.cooked.backend.entity.UserActivityDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface UserActivityDayRepository extends JpaRepository<UserActivityDay, UUID> {

    interface DayCount {
        LocalDate getDay();
        Long getTotal();
    }

    boolean existsByUserIdAndDay(UUID userId, LocalDate day);

    /** Active users per day in [from, to). */
    @Query("SELECT a.day AS day, COUNT(a) AS total FROM UserActivityDay a WHERE a.day >= :from AND a.day < :to GROUP BY a.day")
    List<DayCount> countByDay(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Distinct active users in [from, to). */
    @Query("SELECT COUNT(DISTINCT a.userId) FROM UserActivityDay a WHERE a.day >= :from AND a.day < :to")
    long countDistinctUsers(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT a.day AS day, COUNT(a) AS total FROM UserActivityDay a WHERE a.day >= :from AND a.day < :to AND a.userId IN :users GROUP BY a.day")
    List<DayCount> countByDayForUsers(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("users") java.util.Collection<java.util.UUID> users);

    @Query("SELECT COUNT(DISTINCT a.userId) FROM UserActivityDay a WHERE a.day >= :from AND a.day < :to AND a.userId IN :users")
    long countDistinctUsersForUsers(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("users") java.util.Collection<java.util.UUID> users);

    interface UserDay {
        UUID getUserId();
        LocalDate getDay();
    }

    /** Every (user, day) of activity since [from] (retention). */
    @Query("SELECT a.userId AS userId, a.day AS day FROM UserActivityDay a WHERE a.day >= :from")
    List<UserDay> activitySince(@Param("from") LocalDate from);

    @Query("SELECT MIN(a.day) FROM UserActivityDay a")
    LocalDate firstDay();
}
