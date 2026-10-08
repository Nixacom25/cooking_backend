package com.cooked.backend.repository;

import com.cooked.backend.entity.SiteVisit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface SiteVisitRepository extends JpaRepository<SiteVisit, UUID> {

    @Query("select count(distinct v.visitor) from SiteVisit v where v.day >= :from and v.day < :to")
    long countVisitors(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select count(distinct v.visitor) from SiteVisit v where v.day >= :from and v.day < :to and v.path like '/blog%'")
    long countBlogVisitors(@Param("from") LocalDate from, @Param("to") LocalDate to);

    interface LabelCount {
        String getLabel();
        Long getTotal();
    }

    @Query("select coalesce(v.referrer, 'direct') as label, count(distinct v.visitor) as total from SiteVisit v "
            + "where v.day >= :from and v.day < :to group by coalesce(v.referrer, 'direct') order by count(distinct v.visitor) desc")
    List<LabelCount> visitorsByReferrer(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
