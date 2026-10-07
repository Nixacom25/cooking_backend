package com.cooked.backend.repository;

import com.cooked.backend.entity.ProductEvent;
import com.cooked.backend.entity.ProductEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Product events: inserts, plus aggregate reads for the admin analytics. */
@Repository
public interface ProductEventRepository extends JpaRepository<ProductEvent, UUID> {

    interface TypeSummary {
        ProductEventType getType();
        Long getTotal();
        Long getSuccesses();
        Double getAvgDurationMs();
        Long getUsers();
    }

    interface DayTypeCount {
        LocalDate getDay();
        ProductEventType getType();
        Boolean getSuccess();
        Long getTotal();
    }

    interface LabelCount {
        String getLabel();
        Long getTotal();
        Long getFailures();
    }

    interface ReasonCount {
        ProductEventType getType();
        String getReason();
        Long getTotal();
    }

    @Query("select e.type as type, count(e) as total, "
            + "sum(case when e.success = true then 1 else 0 end) as successes, "
            + "avg(e.durationMs) as avgDurationMs, count(distinct e.userId) as users "
            + "from ProductEvent e where e.createdAt >= :from group by e.type")
    List<TypeSummary> summaryByType(@Param("from") LocalDateTime from);

    @Query("select cast(e.createdAt as LocalDate) as day, e.type as type, e.success as success, count(e) as total "
            + "from ProductEvent e where e.createdAt >= :from "
            + "group by cast(e.createdAt as LocalDate), e.type, e.success")
    List<DayTypeCount> dailyCounts(@Param("from") LocalDateTime from);

    /** Most frequent details (domains, queries) for one type, with their failure count. */
    @Query("select e.detail as label, count(e) as total, "
            + "sum(case when e.success = false then 1 else 0 end) as failures "
            + "from ProductEvent e where e.type = :type and e.createdAt >= :from and e.detail is not null "
            + "group by e.detail order by count(e) desc")
    List<LabelCount> topDetails(@Param("type") ProductEventType type, @Param("from") LocalDateTime from, Pageable page);

    /** Searches that returned nothing, most frequent first. */
    @Query("select e.detail as label, count(e) as total, count(e) as failures "
            + "from ProductEvent e where e.type = com.cooked.backend.entity.ProductEventType.WEB_SEARCH "
            + "and e.success = true and e.resultCount = 0 and e.createdAt >= :from and e.detail is not null "
            + "group by e.detail order by count(e) desc")
    List<LabelCount> zeroResultSearches(@Param("from") LocalDateTime from, Pageable page);

    @Query("select e.type as type, e.failureReason as reason, count(e) as total "
            + "from ProductEvent e where e.success = false and e.createdAt >= :from and e.failureReason is not null "
            + "group by e.type, e.failureReason order by count(e) desc")
    List<ReasonCount> topFailureReasons(@Param("from") LocalDateTime from, Pageable page);

    interface FailureRow {
        UUID getId();
        String getDetail();
        String getReason();
        Integer getDurationMs();
        LocalDateTime getCreatedAt();
        String getEmail();
        String getFirstname();
        String getLastname();
    }

    /** Failed events of a type, newest first, with the user's name (one query per page). */
    @Query(value = "select e.id as id, e.detail as detail, e.failureReason as reason, e.durationMs as durationMs, "
            + "e.createdAt as createdAt, u.email as email, u.firstname as firstname, u.lastname as lastname "
            + "from ProductEvent e left join User u on u.id = e.userId "
            + "where e.type = :type and e.success = false and e.createdAt >= :from order by e.createdAt desc",
            countQuery = "select count(e) from ProductEvent e where e.type = :type and e.success = false and e.createdAt >= :from")
    Page<FailureRow> failures(@Param("type") ProductEventType type, @Param("from") LocalDateTime from, Pageable page);

    long countByTypeAndSuccessFalseAndCreatedAtGreaterThanEqual(ProductEventType type, LocalDateTime from);

    @Query("select min(e.createdAt) from ProductEvent e")
    LocalDateTime firstEventAt();
}
