package com.cooked.backend.repository;

import com.cooked.backend.entity.AlertAck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AlertAckRepository extends JpaRepository<AlertAck, UUID> {

    @Query("select a.alertKey from AlertAck a")
    List<String> findAllKeys();

    @Query("select a.alertKey from AlertAck a where a.alertKey in :keys")
    List<String> findExistingKeys(@org.springframework.data.repository.query.Param("keys") Collection<String> keys);
}
