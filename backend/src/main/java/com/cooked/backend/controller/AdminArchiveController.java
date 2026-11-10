package com.cooked.backend.controller;

import com.cooked.backend.dto.response.ArchiveItemResponse;
import com.cooked.backend.service.ArchiveService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/archive")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminArchiveController {

    private final ArchiveService archiveService;

    @Operation(summary = "Everything deleted: recipes in the trash, recipes deleted by their owner, deleted accounts")
    @GetMapping
    public ResponseEntity<List<ArchiveItemResponse>> list() {
        return ResponseEntity.ok(archiveService.list());
    }
}
