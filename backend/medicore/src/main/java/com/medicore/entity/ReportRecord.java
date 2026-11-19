package com.medicore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(name = "report", uniqueConstraints = {
        @UniqueConstraint(name = "uk_report_request_version", columnNames = {"request_id", "report_version"}),
        @UniqueConstraint(name = "uk_report_request_id_pair", columnNames = {"request_id", "report_id"})
})
public class ReportRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    public TestRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "result_id", nullable = false)
    public TestResult result;

    @Column(name = "report_version", nullable = false)
    public int reportVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "generated_by_user_id", nullable = false)
    public UserAccount generatedBy;

    @Column(name = "report_date", nullable = false)
    public LocalDateTime reportDate;

    @Column(name = "report_file_key", nullable = false, unique = true, length = 255)
    public String reportFileKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_state", nullable = false, length = 16)
    public ReportState reportState = ReportState.CURRENT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_report_id")
    public ReportRecord supersedes;
}
