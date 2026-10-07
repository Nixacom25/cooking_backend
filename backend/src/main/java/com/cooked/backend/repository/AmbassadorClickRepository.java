package com.cooked.backend.repository;

import com.cooked.backend.entity.AmbassadorClick;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AmbassadorClickRepository extends JpaRepository<AmbassadorClick, UUID> {
}
