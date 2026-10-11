package com.cooked.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/** The ZIP of a {@link DataExport}; deleted when the export expires. */
@Entity
@Table(name = "data_export_files")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataExportFile {

    @Id
    private UUID exportId;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(nullable = false, length = 300_000_000)
    private byte[] content;
}
