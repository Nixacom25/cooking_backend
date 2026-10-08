package com.cooked.backend.repository;

import com.cooked.backend.entity.Incident;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    List<Incident> findTop50ByOrderByCreatedAtDesc();

    List<Incident> findByStatusNotOrderByCreatedAtDesc(String status);
}
