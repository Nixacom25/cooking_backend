package com.cooked.backend.controller;

import com.cooked.backend.dto.request.DataExportRequest;
import com.cooked.backend.dto.response.DataExportResponse;
import com.cooked.backend.dto.response.ExportDatasetCount;
import com.cooked.backend.dto.response.MessageResponse;
import com.cooked.backend.service.DataExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Settings → Export data. HTTP only: the work is in {@link DataExportService}. */
@RestController
@RequestMapping("/api/admin/exports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin data export", description = "Workspace exports (ZIP of CSV or JSON), 2FA code required, kept 7 days")
public class AdminExportController {

    private final DataExportService service;

    @GetMapping("/datasets")
    public ResponseEntity<List<ExportDatasetCount>> datasets(@RequestParam(defaultValue = "LAST_12_MONTHS") String period,
                                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(service.datasets(period, from, to));
    }

    @Operation(summary = "Email the admin a 6-digit code to confirm an export")
    @PostMapping("/code")
    public ResponseEntity<MessageResponse> code(Authentication auth) {
        service.sendCode(auth.getName());
        return ResponseEntity.ok(new MessageResponse("Code sent"));
    }

    @PostMapping
    public ResponseEntity<DataExportResponse> request(@Valid @RequestBody DataExportRequest body, Authentication auth) {
        return ResponseEntity.ok(service.request(auth.getName(), body));
    }

    @GetMapping
    public ResponseEntity<Page<DataExportResponse>> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(service.list(page, size));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, Authentication auth) {
        DataExportService.Download d = service.download(id, auth.getName());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(d.filename()).build().toString())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(d.content());
    }
}
