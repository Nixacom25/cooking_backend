package com.cooked.backend.repository;

import com.cooked.backend.entity.AnalyticsEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, Long> {

    @Query("SELECT e.eventName AS name, COUNT(e) AS cnt FROM AnalyticsEvent e GROUP BY e.eventName ORDER BY COUNT(e) DESC")
    List<Object[]> countEventsByNameGrouped();
}

