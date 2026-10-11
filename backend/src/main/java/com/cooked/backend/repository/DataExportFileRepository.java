package com.cooked.backend.repository;

import com.cooked.backend.entity.DataExportFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DataExportFileRepository extends JpaRepository<DataExportFile, UUID> {
}
