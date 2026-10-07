package com.cooked.backend.repository;

import com.cooked.backend.entity.AutomationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AutomationRunRepository extends JpaRepository<AutomationRun, UUID> {

    interface JobSummary {
        String getJob();
        Long getTotal();
        Long getFailures();
        Double getAvgDurationMs();
        LocalDateTime getLastAt();
    }

    @Query("select r.job as job, count(r) as total, sum(case when r.success = false then 1 else 0 end) as failures, "
            + "avg(r.durationMs) as avgDurationMs, max(r.createdAt) as lastAt from AutomationRun r where r.createdAt >= :from group by r.job")
    List<JobSummary> summarySince(@Param("from") LocalDateTime from);

    AutomationRun findFirstByJobOrderByCreatedAtDesc(String job);
}
