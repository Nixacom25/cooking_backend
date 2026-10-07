package com.cooked.backend.repository;

import com.cooked.backend.entity.CostEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CostEntryRepository extends JpaRepository<CostEntry, UUID> {

    /** Entries that may produce cost in [from, to] (a few dozen rows at most). */
    @Query("select c from CostEntry c where c.startDate <= :to and (c.endDate is null or c.endDate >= :from)")
    List<CostEntry> findOverlapping(@Param("from") LocalDate from, @Param("to") LocalDate to);

    List<CostEntry> findAllByOrderByStartDateDesc();
}
