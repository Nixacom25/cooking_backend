package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.DataExportRequest;
import com.cooked.backend.dto.response.DataExportResponse;
import com.cooked.backend.dto.response.ExportDatasetCount;
import com.cooked.backend.entity.DataExport;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.exception.ResourceNotFoundException;
import com.cooked.backend.repository.DataExportFileRepository;
import com.cooked.backend.repository.DataExportRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.ActivityLogService;
import com.cooked.backend.service.AdminTwoFactorService;
import com.cooked.backend.service.DataExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DataExportServiceImpl implements DataExportService {

    static final List<String> ORDER = List.of("users", "recipes", "payments", "costs", "audit");

    private final DataExportRepository exports;
    private final DataExportFileRepository files;
    private final DataExportWriter writer;
    private final AdminTwoFactorService twoFactor;
    private final UserRepository users;
    private final ActivityLogService activityLog;

    /** [first day, last day] of the period; null first day = from the beginning. */
    record Range(LocalDate from, LocalDate to) {
        LocalDate fromOrEpoch() { return from == null ? LocalDate.of(1970, 1, 1) : from; }
        LocalDate toExclusive() { return to.plusDays(1); }
    }

    static Range range(String period, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();
        return switch (period == null ? "" : period.toUpperCase()) {
            case "ALL" -> new Range(null, today);
            case "CUSTOM" -> {
                if (from == null || to == null || to.isBefore(from)) throw new BadRequestException("Choose a start and an end date.");
                yield new Range(from, to.isAfter(today) ? today : to);
            }
            default -> new Range(today.minusMonths(12), today);
        };
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExportDatasetCount> datasets(String period, LocalDate from, LocalDate to) {
        Range r = range(period, from, to);
        LocalDateTime f = r.fromOrEpoch().atStartOfDay();
        LocalDateTime t = r.toExclusive().atStartOfDay();
        return List.of(
                new ExportDatasetCount("users", "Users", "Profiles, preferences, sign-up source", exports.countUsers(f, t)),
                new ExportDatasetCount("recipes", "Recipes", "Library with ingredients and steps", exports.countRecipes(f, t)),
                new ExportDatasetCount("payments", "Payments", "All transactions and statuses", exports.countPayments(f, t)),
                new ExportDatasetCount("costs", "Costs", "Provider costs by day and manual costs",
                        exports.countProviderCosts(r.fromOrEpoch(), r.toExclusive()) + exports.countManualCosts(r.fromOrEpoch(), r.toExclusive())),
                new ExportDatasetCount("audit", "Audit log", "Every recorded admin and intern action", exports.countAudit(f, t)));
    }

    @Override
    public void sendCode(String adminEmail) {
        twoFactor.sendCode(adminEmail);
    }

    @Override
    @Transactional
    public DataExportResponse request(String adminEmail, DataExportRequest req) {
        // Personal data leaves the system: a fresh emailed code is required every time.
        twoFactor.verify(adminEmail, req.code().trim());
        Range r = range(req.period(), req.from(), req.to());
        LinkedHashSet<String> sets = new LinkedHashSet<>();
        ORDER.stream().filter(req.datasets()::contains).forEach(sets::add);
        if (sets.isEmpty()) throw new BadRequestException("Choose at least one dataset.");
        User admin = users.findByEmail(adminEmail).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        DataExport e = exports.save(DataExport.builder()
                .requestedBy(adminEmail)
                .requestedByName(InternMessageServiceImpl.shortName(admin))
                .datasets(String.join(",", sets))
                .format(req.format())
                .fromDay(r.from())
                .toDay(r.to())
                .status(DataExport.Status.PENDING)
                .build());
        activityLog.logActivity(admin, "Data export requested", String.join(", ", sets) + " · " + req.format() + " · "
                + (r.from() == null ? "all time" : r.from() + " → " + r.to()), e.getId(), "DATA_EXPORT");
        UUID id = e.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { writer.run(id); }
            });
        } else {
            writer.run(id);
        }
        return view(e);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DataExportResponse> list(int page, int size) {
        return exports.findAllByOrderByCreatedAtDesc(PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 50)))).map(DataExportServiceImpl::view);
    }

    @Override
    @Transactional
    public Download download(UUID id, String adminEmail) {
        DataExport e = exports.findById(id).orElseThrow(() -> new ResourceNotFoundException("Export not found"));
        if (e.getStatus() != DataExport.Status.READY || (e.getExpiresAt() != null && e.getExpiresAt().isBefore(LocalDateTime.now()))) {
            throw new BadRequestException("This export is not available any more. Prepare a new one.");
        }
        byte[] content = files.findById(id).orElseThrow(() -> new ResourceNotFoundException("Export file not found")).getContent();
        users.findByEmail(adminEmail).ifPresent(admin -> activityLog.logActivity(admin, "Data export downloaded",
                e.getDatasets().replace(",", ", ") + " · " + e.getFormat(), id, "DATA_EXPORT"));
        String name = "cooked-export-" + e.getCreatedAt().toLocalDate() + "-" + id.toString().substring(0, 8) + ".zip";
        return new Download(name, content);
    }

    @Override
    @Transactional
    public void expireOldExports() {
        for (DataExport e : exports.findAllByStatusAndExpiresAtBefore(DataExport.Status.READY, LocalDateTime.now())) {
            files.deleteById(e.getId());
            e.setStatus(DataExport.Status.EXPIRED);
            exports.save(e);
        }
    }

    static DataExportResponse view(DataExport e) {
        return new DataExportResponse(e.getId(), e.getStatus().name(), Arrays.asList(e.getDatasets().split(",")), e.getFormat(),
                e.getFromDay(), e.getToDay(), e.getProgress(), e.getSizeBytes(), e.getRequestedBy(), e.getRequestedByName(),
                e.getCreatedAt(), e.getFinishedAt(), e.getExpiresAt(), e.getError());
    }
}
