package com.cooked.backend.repository;

import com.cooked.backend.entity.SubscriptionStatus;
import com.cooked.backend.entity.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Referral attribution aggregates per ambassador (read-only). Windows are [from, to). */
public interface AmbassadorStatsRepository extends Repository<User, UUID> {

    interface IdCount {
        UUID getId();
        Long getTotal();
    }

    interface IdRevenue {
        UUID getId();
        BigDecimal getAmount();
        Long getPayers();
    }

    interface MonthRevenue {
        Integer getYear();
        Integer getMonth();
        BigDecimal getAmount();
        Long getPayers();
    }

    interface DayCount {
        LocalDate getDay();
        Long getTotal();
    }

    @Query("select u.referredByAmbassadorId as id, count(u) as total from User u where u.referredByAmbassadorId is not null "
            + "and u.referredAt >= :from and u.referredAt < :to group by u.referredByAmbassadorId")
    List<IdCount> referralsBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Referred users currently in one of [statuses] (trial, paid…), referred in [from, to). */
    @Query("select u.referredByAmbassadorId as id, count(u) as total from User u where u.referredByAmbassadorId is not null "
            + "and u.subscriptionStatus in :statuses and u.referredAt >= :from and u.referredAt < :to group by u.referredByAmbassadorId")
    List<IdCount> referralsInStatus(@Param("statuses") Collection<SubscriptionStatus> statuses,
                                    @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Successful payments made by referred users after their referral, in [from, to). */
    @Query("select u.referredByAmbassadorId as id, coalesce(sum(p.amount), 0) as amount, count(distinct u) as payers "
            + "from SubscriptionPayment p join p.user u where u.referredByAmbassadorId is not null and upper(p.status) = 'SUCCESS' "
            + "and p.createdAt >= u.referredAt and p.createdAt >= :from and p.createdAt < :to group by u.referredByAmbassadorId")
    List<IdRevenue> revenueBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select extract(year from p.createdAt) as year, extract(month from p.createdAt) as month, coalesce(sum(p.amount), 0) as amount, "
            + "count(distinct u) as payers from SubscriptionPayment p join p.user u where u.referredByAmbassadorId = :id "
            + "and upper(p.status) = 'SUCCESS' and p.createdAt >= u.referredAt and p.createdAt >= :from "
            + "group by extract(year from p.createdAt), extract(month from p.createdAt)")
    List<MonthRevenue> revenueByMonth(@Param("id") UUID ambassadorId, @Param("from") LocalDateTime from);

    @Query("select c.ambassadorId as id, count(c) as total from AmbassadorClick c where c.createdAt >= :from and c.createdAt < :to group by c.ambassadorId")
    List<IdCount> clicksBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select cast(c.createdAt as LocalDate) as day, count(c) as total from AmbassadorClick c where c.ambassadorId = :id "
            + "and c.createdAt >= :from and c.createdAt < :to group by cast(c.createdAt as LocalDate)")
    List<DayCount> clicksDaily(@Param("id") UUID ambassadorId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
