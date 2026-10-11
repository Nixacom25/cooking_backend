package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.DataExportRequest;
import com.cooked.backend.entity.DataExport;
import com.cooked.backend.entity.User;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.repository.DataExportFileRepository;
import com.cooked.backend.repository.DataExportRepository;
import com.cooked.backend.repository.UserRepository;
import com.cooked.backend.service.ActivityLogService;
import com.cooked.backend.service.AdminTwoFactorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataExportServiceImplTest {

    @Mock private DataExportRepository exports;
    @Mock private DataExportFileRepository files;
    @Mock private DataExportWriter writer;
    @Mock private AdminTwoFactorService twoFactor;
    @Mock private UserRepository users;
    @Mock private ActivityLogService activityLog;
    @InjectMocks private DataExportServiceImpl service;

    private static DataExportRequest req(String code) {
        return new DataExportRequest(List.of("payments", "users"), "CSV", "LAST_12_MONTHS", null, null, code);
    }

    @Test
    void wrongCodeStopsTheExport() {
        doThrow(new BadRequestException("Wrong code")).when(twoFactor).verify("admin@x.com", "000000");
        assertThrows(BadRequestException.class, () -> service.request("admin@x.com", req("000000")));
        verify(exports, never()).save(any());
        verifyNoInteractions(writer);
    }

    @Test
    void validCodeRecordsTheRequestInOrderAndStartsTheWriter() {
        User admin = User.builder().email("admin@x.com").firstname("Cheikh").lastname("Gueye").build();
        when(users.findByEmail("admin@x.com")).thenReturn(Optional.of(admin));
        when(exports.save(any())).thenAnswer(i -> { DataExport e = i.getArgument(0); e.setId(UUID.randomUUID()); e.setCreatedAt(LocalDateTime.now()); return e; });

        var r = service.request("admin@x.com", req("123456"));

        assertEquals(List.of("users", "payments"), r.datasets());
        assertEquals(LocalDate.now().minusMonths(12), r.fromDay());
        verify(writer).run(r.id());
        verify(activityLog).logActivity(eq(admin), eq("Data export requested"), any(), eq(r.id()), eq("DATA_EXPORT"));
    }

    @Test
    void expiredExportCannotBeDownloaded() {
        UUID id = UUID.randomUUID();
        when(exports.findById(id)).thenReturn(Optional.of(DataExport.builder().id(id).status(DataExport.Status.READY)
                .datasets("users").format("CSV").expiresAt(LocalDateTime.now().minusHours(1)).build()));
        assertThrows(BadRequestException.class, () -> service.download(id, "admin@x.com"));
        verifyNoInteractions(files);
    }

    @Test
    void customPeriodNeedsBothDates() {
        assertThrows(BadRequestException.class, () -> DataExportServiceImpl.range("CUSTOM", LocalDate.now(), null));
        assertNull(DataExportServiceImpl.range("ALL", null, null).from());
    }

    @Test
    void csvCellsAreEscapedAndFormulasNeutralised() {
        assertEquals("\"a,b\"", DataExportWriter.csv("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", DataExportWriter.csv("say \"hi\""));
        assertEquals("'=HYPERLINK(1)", DataExportWriter.csv("=HYPERLINK(1)"));
        assertEquals("-5", DataExportWriter.csv(-5));
        assertEquals("", DataExportWriter.csv(null));
    }
}
