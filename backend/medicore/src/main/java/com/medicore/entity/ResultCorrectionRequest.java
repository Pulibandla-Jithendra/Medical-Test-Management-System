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
@Table(name = "result_correction_request", uniqueConstraints = {
        @UniqueConstraint(name = "uk_correction_request_id_pair", columnNames = {"request_id", "correction_request_id"})
})
public class ResultCorrectionRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "correction_request_id")
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    public TestRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_result_id", nullable = false)
    public TestResult targetResult;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_report_id")
    public ReportRecord targetReport;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_user_id", nullable = false)
    public UserAccount requestedBy;

    @Column(nullable = false, length = 1000)
    public String reason;

    @Column(name = "requested_at", nullable = false)
    public LocalDateTime requestedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public CorrectionStatus status = CorrectionStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    public UserAccount reviewedBy;

    @Column(name = "reviewed_at")
    public LocalDateTime reviewedAt;

    @Column(name = "decision_note", length = 1000)
    public String decisionNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applied_result_id")
    public TestResult appliedResult;
}
