package com.cooked.backend.repository;

import com.cooked.backend.entity.SupportTicket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {
    Page<SupportTicket> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<SupportTicket> findAllByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    /** An intern's own messages to the recipe team, newest first. */
    java.util.List<SupportTicket> findAllBySourceAndUserIdOrderByCreatedAtDesc(String source, String userId);
}
