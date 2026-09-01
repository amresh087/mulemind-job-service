package com.mulemind.job.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "job_status_history")
public class JobStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 36, nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private DocumentRecord document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_id", nullable = false)
    private JobStatus status;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;
}
