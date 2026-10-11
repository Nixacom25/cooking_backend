package com.cooked.backend.service;

import com.cooked.backend.dto.request.DataExportRequest;
import com.cooked.backend.dto.response.DataExportResponse;
import com.cooked.backend.dto.response.ExportDatasetCount;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Settings → Export data: workspace exports (ZIP of CSV or JSON), kept 7 days. */
public interface DataExportService {

    /** The exportable datasets with their row count for the period. */
    List<ExportDatasetCount> datasets(String period, LocalDate from, LocalDate to);

    /** Emails the admin a 6-digit code (exports contain personal data). */
    void sendCode(String adminEmail);

    /** Checks the code, records the request and starts building the file in the background. */
    DataExportResponse request(String adminEmail, DataExportRequest request);

    Page<DataExportResponse> list(int page, int size);

    /** The ZIP of a ready export; every download is written to the audit log. */
    Download download(UUID id, String adminEmail);

    /** Deletes the files of exports older than 7 days (daily job). */
    void expireOldExports();

    record Download(String filename, byte[] content) {
    }
}
