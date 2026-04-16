package com.cooked.backend.repository;

import com.cooked.backend.entity.DeviceSession;
import com.cooked.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceSessionRepository extends JpaRepository<DeviceSession, UUID> {
    List<DeviceSession> findByUserOrderByLastActiveDesc(User user);

    Optional<DeviceSession> findByToken(String token);
}
