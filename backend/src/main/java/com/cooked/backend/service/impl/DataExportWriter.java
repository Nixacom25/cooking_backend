package com.cooked.backend.service.impl;

import com.cooked.backend.entity.DataExport;
import com.cooked.backend.entity.DataExportFile;
import com.cooked.backend.repository.DataExportFileRepository;
import com.cooked.backend.repository.DataExportRepository;
import com.cooked.backend.service.EmailService;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds an export in the background: one file per dataset in a ZIP, read
 * page by page (1,000 rows) so memory stays bounded by the file itself.
 */
@Slf4j
@Component
public class DataExportWriter {

    static final int PAGE = 1000;
    static final long MAX_BYTES = 250L * 1024 * 1024;
    static final int KEEP_DAYS = 7;

    private final DataExportRepository exports;
    private final DataExportFileRepository files;
    private final EmailService emailService;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;

    public DataExportWriter(DataExportRepository exports, DataExportFileRepository files, EmailService emailService,
                            PlatformTransactionManager txManager) {
        this.exports = exports;
        this.files = files;
        this.emailService = emailService;
        this.tx = new TransactionTemplate(txManager);
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
    }

    @Async
    public void run(UUID id) {
        DataExport e = exports.findById(id).orElse(null);
        if (e == null || e.getStatus() != DataExport.Status.PENDING) return;
        update(id, x -> { x.setStatus(DataExport.Status.RUNNING); x.setProgress(1); });
        try {
            byte[] zip = build(e);
            tx.executeWithoutResult(s -> {
                files.save(new DataExportFile(id, zip));
                DataExport x = exports.findById(id).orElseThrow();
                x.setStatus(DataExport.Status.READY);
                x.setProgress(100);
                x.setSizeBytes((long) zip.length);
                x.setFinishedAt(LocalDateTime.now());
                x.setExpiresAt(LocalDateTime.now().plusDays(KEEP_DAYS));
                exports.save(x);
            });
            notifyReady(e, zip.length);
        } catch (Exception ex) {
            log.error("Data export {} failed: {}", id, ex.getMessage());
            String msg = ex.getMessage() == null ? "Export failed" : ex.getMessage();
            update(id, x -> { x.setStatus(DataExport.Status.FAILED); x.setError(msg.length() > 480 ? msg.substring(0, 480) : msg); x.setFinishedAt(LocalDateTime.now()); });
        }
    }

    byte[] build(DataExport e) throws IOException {
        LocalDate fromDay = e.getFromDay() == null ? LocalDate.of(1970, 1, 1) : e.getFromDay();
        LocalDate toDay = (e.getToDay() == null ? LocalDate.now() : e.getToDay()).plusDays(1); // exclusive
        LocalDateTime from = fromDay.atStartOfDay();
        LocalDateTime to = toDay.atStartOfDay();
        boolean json = "JSON".equalsIgnoreCase(e.getFormat());
        List<String> sets = Arrays.asList(e.getDatasets().split(","));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            int done = 0;
            for (String set : sets) {
                switch (set.trim()) {
                    case "users" -> write(zip, "users", json, USER_COLS, p -> readTx.execute(s -> exports.users(from, to, p)), Function.identity(), bytes);
                    case "recipes" -> write(zip, "recipes", json, RECIPE_COLS, p -> readTx.execute(s -> withIngredientsAndSteps(exports.recipes(from, to, p))), Function.identity(), bytes);
                    case "payments" -> write(zip, "payments", json, PAYMENT_COLS, p -> readTx.execute(s -> exports.payments(from, to, p)), Function.identity(), bytes);
                    case "costs" -> {
                        write(zip, "costs_providers", json, PROVIDER_COST_COLS, p -> readTx.execute(s -> exports.providerCosts(fromDay, toDay, p)), Function.identity(), bytes);
                        write(zip, "costs_manual", json, MANUAL_COST_COLS, p -> readTx.execute(s -> exports.manualCosts(fromDay, toDay, p)), Function.identity(), bytes);
                    }
                    case "audit" -> write(zip, "audit_log", json, AUDIT_COLS, p -> readTx.execute(s -> exports.audit(from, to, p)), Function.identity(), bytes);
                    default -> { }
                }
                done++;
                int progress = Math.min(95, 5 + (90 * done) / sets.size());
                update(e.getId(), x -> x.setProgress(progress));
            }
        }
        return bytes.toByteArray();
    }

    static final List<String> USER_COLS = List.of("id", "email", "firstname", "lastname", "role", "status", "subscription_status",
            "subscription_type", "subscription_expires_at", "country", "language", "discovery_source", "created_at", "last_active");
    static final List<String> RECIPE_COLS = List.of("id", "name", "origin", "cuisine", "prep_time_min", "cook_time_min", "servings",
            "kcal", "published", "public", "deleted", "source_url", "created_at", "ingredients", "steps");
    static final List<String> PAYMENT_COLS = List.of("id", "created_at", "customer_email", "store", "plan", "amount_usd", "status", "transaction");
    static final List<String> PROVIDER_COST_COLS = List.of("day", "provider", "line_item", "amount_usd");
    static final List<String> MANUAL_COST_COLS = List.of("start_date", "end_date", "provider", "category", "frequency", "amount", "currency", "amount_usd", "notes");
    static final List<String> AUDIT_COLS = List.of("created_at", "actor_email", "action", "details", "entity_type", "entity_id");

    /** Adds "qty name; …" ingredients and "1. … | 2. …" steps to recipe rows (2 queries per page). */
    private List<Object[]> withIngredientsAndSteps(List<Object[]> rows) {
        if (rows.isEmpty()) return rows;
        List<UUID> ids = rows.stream().map(r -> (UUID) r[0]).toList();
        Map<UUID, List<String>> ings = new HashMap<>();
        for (Object[] r : exports.recipeIngredients(ids)) {
            String q = r[1] == null ? "" : r[1].toString().trim();
            ings.computeIfAbsent((UUID) r[0], k -> new ArrayList<>()).add((q.isEmpty() ? "" : q + " ") + r[2]);
        }
        Map<UUID, List<String>> steps = new HashMap<>();
        for (Object[] r : exports.recipeSteps(ids)) steps.computeIfAbsent((UUID) r[0], k -> new ArrayList<>()).add(String.valueOf(r[1]));
        List<Object[]> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            Object[] x = Arrays.copyOf(r, r.length + 2);
            x[r.length] = String.join("; ", ings.getOrDefault((UUID) r[0], List.of()));
            List<String> st = steps.getOrDefault((UUID) r[0], List.of());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < st.size(); i++) sb.append(i == 0 ? "" : " | ").append(i + 1).append(". ").append(st.get(i));
            x[r.length + 1] = sb.toString();
            out.add(x);
        }
        return out;
    }

    private void write(ZipOutputStream zip, String name, boolean json, List<String> cols,
                       Function<PageRequest, List<Object[]>> page, Function<Object[], Object[]> map, ByteArrayOutputStream size) throws IOException {
        zip.putNextEntry(new ZipEntry(name + (json ? ".json" : ".csv")));
        OutputStream entry = new FilterOutputStream(zip) {
            @Override public void close() throws IOException { flush(); } // keep the zip open
            @Override public void write(byte[] b, int off, int len) throws IOException { out.write(b, off, len); }
        };
        if (json) {
            try (JsonGenerator g = new JsonFactory().createGenerator(entry, JsonEncoding.UTF8)) {
                g.writeStartArray();
                forEachRow(page, map, size, r -> {
                    g.writeStartObject();
                    for (int i = 0; i < cols.size(); i++) writeJson(g, cols.get(i), i < r.length ? r[i] : null);
                    g.writeEndObject();
                });
                g.writeEndArray();
            }
        } else {
            try (Writer w = new OutputStreamWriter(entry, StandardCharsets.UTF_8)) {
                w.write('﻿'); // Excel opens UTF-8 correctly with a BOM
                w.write(String.join(",", cols));
                w.write("\r\n");
                forEachRow(page, map, size, r -> {
                    StringBuilder line = new StringBuilder();
                    for (int i = 0; i < cols.size(); i++) {
                        if (i > 0) line.append(',');
                        line.append(csv(i < r.length ? r[i] : null));
                    }
                    w.write(line.append("\r\n").toString());
                });
            }
        }
        zip.closeEntry();
    }

    interface RowWriter { void write(Object[] row) throws IOException; }

    private void forEachRow(Function<PageRequest, List<Object[]>> page, Function<Object[], Object[]> map,
                            ByteArrayOutputStream size, RowWriter writer) throws IOException {
        for (int p = 0; ; p++) {
            List<Object[]> rows = page.apply(PageRequest.of(p, PAGE));
            if (rows == null || rows.isEmpty()) return;
            for (Object[] r : rows) writer.write(map.apply(r));
            if (size.size() > MAX_BYTES) throw new IOException("The export is larger than 250 MB. Choose a shorter period or fewer datasets.");
            if (rows.size() < PAGE) return;
        }
    }

    /** CSV cell: quoted when needed; a leading = + - @ is neutralised (formula injection). */
    static String csv(Object v) {
        if (v == null) return "";
        String s = v instanceof BigDecimal b ? b.toPlainString() : v.toString();
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0 && !(v instanceof Number)) s = "'" + s;
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) s = "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    private static void writeJson(JsonGenerator g, String field, Object v) throws IOException {
        if (v == null) g.writeNullField(field);
        else if (v instanceof BigDecimal b) g.writeNumberField(field, b);
        else if (v instanceof Integer i) g.writeNumberField(field, i);
        else if (v instanceof Long l) g.writeNumberField(field, l);
        else if (v instanceof Double d) g.writeNumberField(field, d);
        else if (v instanceof Boolean b) g.writeBooleanField(field, b);
        else g.writeStringField(field, v.toString());
    }

    private void notifyReady(DataExport e, long bytes) {
        try {
            emailService.sendAdminSummaryEmail(e.getRequestedBy(), "Your Cooked export is ready", "Your data export is ready",
                    List.of(new String[]{"Datasets", e.getDatasets().replace(",", ", ")},
                            new String[]{"Format", e.getFormat()},
                            new String[]{"Size", String.format(Locale.US, "%.1f MB", bytes / 1048576.0)},
                            new String[]{"Download", "Settings → Export data in the backoffice (link valid 7 days)"}));
        } catch (Exception ex) {
            log.warn("Export ready email failed: {}", ex.getMessage());
        }
    }

    private void update(UUID id, java.util.function.Consumer<DataExport> change) {
        tx.executeWithoutResult(s -> exports.findById(id).ifPresent(x -> { change.accept(x); exports.save(x); }));
    }
}
