package com.medicore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "test_result", uniqueConstraints = {
        @UniqueConstraint(name = "uk_result_request_version", columnNames = {"request_id", "version_no"}),
        @UniqueConstraint(name = "uk_result_request_id_pair", columnNames = {"request_id", "result_id"}),
        @UniqueConstraint(name = "uk_result_supersedes", columnNames = "supersedes_result_id")
})
public class TestResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "result_id")
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    public TestRequest request;

    @Column(name = "version_no", nullable = false)
    public int versionNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_result_id")
    public TestResult supersedes;

    @Column(name = "result_value", nullable = false, columnDefinition = "text")
    public String resultValue;

    @Column(columnDefinition = "text")
    public String remarks;

    @Column(name = "result_date", nullable = false)
    public LocalDate resultDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entered_by_user_id", nullable = false)
    public UserAccount enteredBy;

    @Column(name = "entered_at", nullable = false)
    public LocalDateTime enteredAt;

    @Column(name = "correction_reason", length = 1000)
    public String correctionReason;
}
