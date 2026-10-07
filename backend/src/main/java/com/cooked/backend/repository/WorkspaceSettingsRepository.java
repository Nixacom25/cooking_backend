package com.cooked.backend.repository;

import com.cooked.backend.entity.WorkspaceSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceSettingsRepository extends JpaRepository<WorkspaceSettings, Long> {
}
