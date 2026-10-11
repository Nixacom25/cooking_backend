package com.cooked.backend.repository;

import com.cooked.backend.entity.CompetitorSite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CompetitorSiteRepository extends JpaRepository<CompetitorSite, UUID> {
    List<CompetitorSite> findAllByOrderByCreatedAtAsc();
    boolean existsByUrl(String url);
    long count();
}
