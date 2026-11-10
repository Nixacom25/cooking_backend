package com.cooked.backend.repository;

import com.cooked.backend.entity.FormerAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FormerAccountRepository extends JpaRepository<FormerAccount, UUID> {

    boolean existsByEmailHash(String emailHash);
}
