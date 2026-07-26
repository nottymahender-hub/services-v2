package com.dbs.mot.grc.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Entity for the {@code orl_static_data_maintianance_csv_upload_audit} table —
 * one row per successful CSV upload.
 */
@Table("orl_static_data_maintianance_csv_upload_audit")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CsvUploadAudit {

    @Id
    @Column("id")
    private Long id;

    @Column("file_name")
    private String fileName;

    @Column("table_name")
    private String tableName;

    @Column("uploaded_by")
    private String uploadedBy;

    @ReadOnlyProperty
    @Column("uploaded_dt_tm")
    private LocalDateTime uploadedDtTm;

    @Column("row_count")
    private Integer rowCount;

    @Column("status")
    private String status;
}
