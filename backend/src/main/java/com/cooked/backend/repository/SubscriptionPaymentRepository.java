package com.cooked.backend.repository;

import com.cooked.backend.entity.SubscriptionPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, UUID> {
    List<SubscriptionPayment> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    boolean existsByStripePaymentId(String stripePaymentId);

    // --- Aggregates for the admin revenue summary (computed by the database,
    // never by loading every payment in memory). Status values are stored in
    // upper case ("SUCCESS", "FAILED", ...) but compared case-insensitively.

    /** Amount per label (store or plan type). */
    interface LabelAmount {
        String getLabel();
        BigDecimal getAmount();
    }

    /** Amount per calendar day. */
    interface DayAmount {
        LocalDate getDay();
        BigDecimal getAmount();
    }

    /** Amount per calendar month. */
    interface MonthAmount {
        Integer getYear();
        Integer getMonth();
        BigDecimal getAmount();
    }

    @Query("select coalesce(sum(p.amount), 0) from SubscriptionPayment p "
            + "where upper(p.status) = 'SUCCESS' and p.createdAt >= :from and p.createdAt < :to")
    BigDecimal sumSuccessBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select coalesce(sum(p.amount), 0) from SubscriptionPayment p where upper(p.status) = 'SUCCESS'")
    BigDecimal sumSuccess();

    @Query("select count(distinct p.user.id) from SubscriptionPayment p where upper(p.status) = 'SUCCESS'")
    long countDistinctPayers();

    @Query("select count(p) from SubscriptionPayment p where upper(p.status) = 'SUCCESS' and p.createdAt >= :from")
    long countSuccessSince(@Param("from") LocalDateTime from);

    /** Payments whose status contains [keyword] (e.g. FAIL, REFUND) since [from]. */
    @Query("select count(p) from SubscriptionPayment p "
            + "where upper(p.status) like concat('%', :keyword, '%') and p.createdAt >= :from")
    long countByStatusKeywordSince(@Param("keyword") String keyword, @Param("from") LocalDateTime from);

    /** Successful payments since [from] by users who had already paid before (renewals). */
    @Query("select count(p) from SubscriptionPayment p where upper(p.status) = 'SUCCESS' and p.createdAt >= :from "
            + "and exists (select 1 from SubscriptionPayment q where q.user = p.user "
            + "and upper(q.status) = 'SUCCESS' and q.createdAt < p.createdAt)")
    long countRenewalsSince(@Param("from") LocalDateTime from);

    @Query("select coalesce(p.store, 'Other') as label, sum(p.amount) as amount from SubscriptionPayment p "
            + "where upper(p.status) = 'SUCCESS' and p.createdAt >= :from group by coalesce(p.store, 'Other')")
    List<LabelAmount> sumSuccessByStoreSince(@Param("from") LocalDateTime from);

    @Query("select p.planType as label, sum(p.amount) as amount from SubscriptionPayment p "
            + "where upper(p.status) = 'SUCCESS' and p.createdAt >= :from group by p.planType")
    List<LabelAmount> sumSuccessByPlanTypeSince(@Param("from") LocalDateTime from);

    @Query("select cast(p.createdAt as LocalDate) as day, sum(p.amount) as amount from SubscriptionPayment p "
            + "where upper(p.status) = 'SUCCESS' and p.createdAt >= :from group by cast(p.createdAt as LocalDate)")
    List<DayAmount> sumSuccessByDaySince(@Param("from") LocalDateTime from);

    @Query("select extract(year from p.createdAt) as year, extract(month from p.createdAt) as month, "
            + "sum(p.amount) as amount from SubscriptionPayment p "
            + "where upper(p.status) = 'SUCCESS' and p.createdAt >= :from "
            + "group by extract(year from p.createdAt), extract(month from p.createdAt)")
    List<MonthAmount> sumSuccessByMonthSince(@Param("from") LocalDateTime from);
}
