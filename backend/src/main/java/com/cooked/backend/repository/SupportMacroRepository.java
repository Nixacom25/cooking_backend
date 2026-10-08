package com.cooked.backend.repository;

import com.cooked.backend.entity.SupportMacro;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupportMacroRepository extends JpaRepository<SupportMacro, UUID> {

    List<SupportMacro> findAllByOrderByTitleAsc();
}
