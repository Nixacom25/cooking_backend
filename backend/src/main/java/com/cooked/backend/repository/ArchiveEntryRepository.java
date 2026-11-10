package com.cooked.backend.repository;

import com.cooked.backend.entity.ArchiveEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ArchiveEntryRepository extends JpaRepository<ArchiveEntry, UUID> {

    List<ArchiveEntry> findTop500ByOrderByCreatedAtDesc();
}
