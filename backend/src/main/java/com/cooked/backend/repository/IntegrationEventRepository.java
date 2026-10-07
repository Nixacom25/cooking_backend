package com.cooked.backend.repository;

import com.cooked.backend.entity.IntegrationEvent;
import com.cooked.backend.entity.IntegrationKey;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface IntegrationEventRepository extends JpaRepository<IntegrationEvent, UUID> {

    interface KeySummary {
        IntegrationKey getIntegration();
        Long getTotal();
        Long getFailures();
        LocalDateTime getLastAt();
    }

    interface NameSummary {
        String getName();
        Long getTotal();
        Long getFailures();
        LocalDateTime getLastAt();
    }

    interface DayCount {
        LocalDate getDay();
        Long getTotal();
        Long getFailures();
    }

    @Query("select e.integration as integration, count(e) as total, "
            + "sum(case when e.success = false then 1 else 0 end) as failures, max(e.createdAt) as lastAt "
            + "from IntegrationEvent e where e.createdAt >= :from group by e.integration")
    List<KeySummary> summarySince(@Param("from") LocalDateTime from);

    @Query("select e.name as name, count(e) as total, sum(case when e.success = false then 1 else 0 end) as failures, max(e.createdAt) as lastAt "
            + "from IntegrationEvent e where e.integration = :key and e.createdAt >= :from group by e.name order by count(e) desc")
    List<NameSummary> byName(@Param("key") IntegrationKey key, @Param("from") LocalDateTime from);

    @Query("select cast(e.createdAt as LocalDate) as day, count(e) as total, sum(case when e.success = false then 1 else 0 end) as failures "
            + "from IntegrationEvent e where e.integration = :key and e.createdAt >= :from group by cast(e.createdAt as LocalDate)")
    List<DayCount> daily(@Param("key") IntegrationKey key, @Param("from") LocalDateTime from);

    @Query("select min(e.createdAt) from IntegrationEvent e where e.integration = :key")
    LocalDateTime firstAt(@Param("key") IntegrationKey key);

    Page<IntegrationEvent> findByIntegrationOrderByCreatedAtDesc(IntegrationKey integration, Pageable page);
}
