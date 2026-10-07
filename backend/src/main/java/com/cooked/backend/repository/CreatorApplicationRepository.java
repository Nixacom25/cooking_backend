package com.cooked.backend.repository;

import com.cooked.backend.entity.CreatorApplication;
import com.cooked.backend.entity.CreatorApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface CreatorApplicationRepository extends JpaRepository<CreatorApplication, UUID> {

    interface StatusCount {
        CreatorApplicationStatus getStatus();
        Long getTotal();
    }

    Page<CreatorApplication> findByStatusOrderByCreatedAtDesc(CreatorApplicationStatus status, Pageable page);

    Page<CreatorApplication> findAllByOrderByCreatedAtDesc(Pageable page);

    @Query("select a.status as status, count(a) as total from CreatorApplication a group by a.status")
    List<StatusCount> countByStatus();
}
