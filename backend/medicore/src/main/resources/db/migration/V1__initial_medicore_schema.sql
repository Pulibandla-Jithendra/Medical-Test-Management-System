CREATE TABLE `user` (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    security_version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id),
    CONSTRAINT uk_user_email UNIQUE (email),
    CONSTRAINT ck_user_role CHECK (role IN ('PATIENT', 'DOCTOR', 'LAB_TECHNICIAN', 'ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE patient (
    patient_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    age INT NULL,
    gender VARCHAR(64) NULL,
    phone VARCHAR(32) NULL,
    address VARCHAR(255) NULL,
    PRIMARY KEY (patient_id),
    CONSTRAINT uk_patient_user UNIQUE (user_id),
    CONSTRAINT fk_patient_user FOREIGN KEY (user_id) REFERENCES `user` (user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_patient_age CHECK (age IS NULL OR age BETWEEN 1 AND 120)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE doctor (
    doctor_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    specialization VARCHAR(160) NOT NULL,
    PRIMARY KEY (doctor_id),
    CONSTRAINT uk_doctor_user UNIQUE (user_id),
    CONSTRAINT fk_doctor_user FOREIGN KEY (user_id) REFERENCES `user` (user_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE medical_test (
    test_id BIGINT NOT NULL AUTO_INCREMENT,
    test_name VARCHAR(160) NOT NULL,
    description TEXT NULL,
    price DECIMAL(10,2) NOT NULL,
    reference_range VARCHAR(255) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (test_id),
    CONSTRAINT ck_medical_test_price CHECK (price >= 0),
    INDEX ix_medical_test_active_name (active, test_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE doctor_patient_assignment (
    assignment_id BIGINT NOT NULL AUTO_INCREMENT,
    doctor_id BIGINT NOT NULL,
    patient_id BIGINT NOT NULL,
    assigned_by_user_id BIGINT NOT NULL,
    assigned_at DATETIME(6) NOT NULL,
    revoked_by_user_id BIGINT NULL,
    revoked_at DATETIME(6) NULL,
    PRIMARY KEY (assignment_id),
    CONSTRAINT uk_assignment_identity UNIQUE (assignment_id, doctor_id, patient_id),
    CONSTRAINT fk_assignment_doctor FOREIGN KEY (doctor_id) REFERENCES doctor (doctor_id) ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_patient FOREIGN KEY (patient_id) REFERENCES patient (patient_id) ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_assigned_by FOREIGN KEY (assigned_by_user_id) REFERENCES `user` (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_revoked_by FOREIGN KEY (revoked_by_user_id) REFERENCES `user` (user_id) ON DELETE RESTRICT,
    INDEX ix_assignment_doctor_patient_revoked (doctor_id, patient_id, revoked_at),
    INDEX ix_assignment_patient_revoked (patient_id, revoked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE patient_consent (
    consent_id BIGINT NOT NULL AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL,
    consented_by_user_id BIGINT NOT NULL,
    consent_text_version VARCHAR(32) NOT NULL,
    consented_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    revoked_by_user_id BIGINT NULL,
    PRIMARY KEY (consent_id),
    CONSTRAINT fk_consent_assignment FOREIGN KEY (assignment_id) REFERENCES doctor_patient_assignment (assignment_id) ON DELETE RESTRICT,
    CONSTRAINT fk_consent_granted_by FOREIGN KEY (consented_by_user_id) REFERENCES `user` (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_consent_revoked_by FOREIGN KEY (revoked_by_user_id) REFERENCES `user` (user_id) ON DELETE RESTRICT,
    INDEX ix_consent_assignment_active (assignment_id, revoked_at),
    INDEX ix_consent_patient (consented_by_user_id, revoked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE test_request (
    request_id BIGINT NOT NULL AUTO_INCREMENT,
    patient_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    doctor_patient_assignment_id BIGINT NOT NULL,
    test_id BIGINT NOT NULL,
    assigned_technician_user_id BIGINT NULL,
    request_date DATETIME(6) NOT NULL,
    scheduled_date DATE NULL,
    scheduled_time TIME NULL,
    status VARCHAR(32) NOT NULL,
    PRIMARY KEY (request_id),
    CONSTRAINT fk_request_patient FOREIGN KEY (patient_id) REFERENCES patient (patient_id) ON DELETE RESTRICT,
    CONSTRAINT fk_request_doctor FOREIGN KEY (doctor_id) REFERENCES doctor (doctor_id) ON DELETE RESTRICT,
    CONSTRAINT fk_request_assignment FOREIGN KEY (doctor_patient_assignment_id) REFERENCES doctor_patient_assignment (assignment_id) ON DELETE RESTRICT,
    CONSTRAINT fk_request_assignment_match FOREIGN KEY (doctor_patient_assignment_id, doctor_id, patient_id) REFERENCES doctor_patient_assignment (assignment_id, doctor_id, patient_id) ON DELETE RESTRICT,
    CONSTRAINT fk_request_test FOREIGN KEY (test_id) REFERENCES medical_test (test_id) ON DELETE RESTRICT,
    CONSTRAINT fk_request_technician FOREIGN KEY (assigned_technician_user_id) REFERENCES `user` (user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_request_status CHECK (status IN ('REQUESTED', 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'REPORT_GENERATED')),
    CONSTRAINT ck_request_schedule_pair CHECK ((scheduled_date IS NULL) = (scheduled_time IS NULL)),
    CONSTRAINT ck_request_schedule_assignment CHECK (status = 'REQUESTED' OR (scheduled_date IS NOT NULL AND assigned_technician_user_id IS NOT NULL)),
    INDEX ix_request_patient_date (patient_id, request_date),
    INDEX ix_request_doctor_date (doctor_id, request_date),
    INDEX ix_request_technician_status_schedule (assigned_technician_user_id, status, scheduled_date, scheduled_time),
    INDEX ix_request_status_date (status, request_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE test_result (
    result_id BIGINT NOT NULL AUTO_INCREMENT,
    request_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    supersedes_result_id BIGINT NULL,
    result_value TEXT NOT NULL,
    remarks TEXT NULL,
    result_date DATE NOT NULL,
    entered_by_user_id BIGINT NOT NULL,
    entered_at DATETIME(6) NOT NULL,
    correction_reason VARCHAR(1000) NULL,
    PRIMARY KEY (result_id),
    CONSTRAINT uk_result_request_version UNIQUE (request_id, version_no),
    CONSTRAINT uk_result_request_id_pair UNIQUE (request_id, result_id),
    CONSTRAINT uk_result_supersedes UNIQUE (supersedes_result_id),
    CONSTRAINT fk_result_request FOREIGN KEY (request_id) REFERENCES test_request (request_id) ON DELETE RESTRICT,
    CONSTRAINT fk_result_supersedes FOREIGN KEY (supersedes_result_id) REFERENCES test_result (result_id) ON DELETE RESTRICT,
    CONSTRAINT fk_result_entered_by FOREIGN KEY (entered_by_user_id) REFERENCES `user` (user_id) ON DELETE RESTRICT,
    CONSTRAINT ck_result_version CHECK (version_no >= 1),
    INDEX ix_result_request_version (request_id, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE report (
    report_id BIGINT NOT NULL AUTO_INCREMENT,
    request_id BIGINT NOT NULL,
    result_id BIGINT NOT NULL,
    report_version INT NOT NULL,
    generated_by_user_id BIGINT NOT NULL,
    report_date DATETIME(6) NOT NULL,
    report_file_key VARCHAR(255) NOT NULL,
    report_state VARCHAR(16) NOT NULL,
    supersedes_report_id BIGINT NULL,
    PRIMARY KEY (report_id),
    CONSTRAINT uk_report_request_version UNIQUE (request_id, report_version),
    CONSTRAINT uk_report_request_id_pair UNIQUE (request_id, report_id),
    CONSTRAINT uk_report_file_key UNIQUE (report_file_key),
    CONSTRAINT fk_report_request FOREIGN KEY (request_id) REFERENCES test_request (request_id) ON DELETE RESTRICT,
    CONSTRAINT fk_report_result FOREIGN KEY (result_id) REFERENCES test_result (result_id) ON DELETE RESTRICT,
    CONSTRAINT fk_report_result_request FOREIGN KEY (request_id, result_id) REFERENCES test_result (request_id, result_id) ON DELETE RESTRICT,
    CONSTRAINT fk_report_generated_by FOREIGN KEY (generated_by_user_id) REFERENCES `user` (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_report_supersedes FOREIGN KEY (supersedes_report_id) REFERENCES report (report_id) ON DELETE RESTRICT,
    CONSTRAINT ck_report_version CHECK (report_version >= 1),
    CONSTRAINT ck_report_state CHECK (report_state IN ('CURRENT', 'SUPERSEDED')),
    INDEX ix_report_request_version (request_id, report_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE result_correction_request (
    correction_request_id BIGINT NOT NULL AUTO_INCREMENT,
    request_id BIGINT NOT NULL,
    target_result_id BIGINT NOT NULL,
    target_report_id BIGINT NULL,
    requested_by_user_id BIGINT NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    status VARCHAR(16) NOT NULL,
    reviewed_by_user_id BIGINT NULL,
    reviewed_at DATETIME(6) NULL,
    decision_note VARCHAR(1000) NULL,
    applied_result_id BIGINT NULL,
    PRIMARY KEY (correction_request_id),
    CONSTRAINT uk_correction_request_id_pair UNIQUE (request_id, correction_request_id),
    CONSTRAINT fk_correction_request FOREIGN KEY (request_id) REFERENCES test_request (request_id) ON DELETE RESTRICT,
    CONSTRAINT fk_correction_target_result FOREIGN KEY (target_result_id) REFERENCES test_result (result_id) ON DELETE RESTRICT,
    CONSTRAINT fk_correction_target_result_request FOREIGN KEY (request_id, target_result_id) REFERENCES test_result (request_id, result_id) ON DELETE RESTRICT,
    CONSTRAINT fk_correction_target_report FOREIGN KEY (target_report_id) REFERENCES report (report_id) ON DELETE RESTRICT,
    CONSTRAINT fk_correction_target_report_request FOREIGN KEY (request_id, target_report_id) REFERENCES report (request_id, report_id) ON DELETE RESTRICT,
    CONSTRAINT fk_correction_requested_by FOREIGN KEY (requested_by_user_id) REFERENCES `user` (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_correction_reviewed_by FOREIGN KEY (reviewed_by_user_id) REFERENCES `user` (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_correction_applied_result FOREIGN KEY (applied_result_id) REFERENCES test_result (result_id) ON DELETE RESTRICT,
    CONSTRAINT fk_correction_applied_result_request FOREIGN KEY (request_id, applied_result_id) REFERENCES test_result (request_id, result_id) ON DELETE RESTRICT,
    CONSTRAINT ck_correction_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'APPLIED')),
    INDEX ix_correction_status_target (status, request_id, target_result_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE refresh_token (
    token_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    family_id CHAR(36) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    issued_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    revoked_at DATETIME(6) NULL,
    replaced_by_token_id CHAR(36) NULL,
    PRIMARY KEY (token_id),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES `user` (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_refresh_replaced_by FOREIGN KEY (replaced_by_token_id) REFERENCES refresh_token (token_id) ON DELETE RESTRICT,
    INDEX ix_refresh_family_active (family_id, revoked_at, used_at, expires_at),
    INDEX ix_refresh_user_expiry (user_id, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE audit_log (
    audit_id BIGINT NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT NULL,
    subject_user_id BIGINT NULL,
    action VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id VARCHAR(64) NULL,
    occurred_at DATETIME(6) NOT NULL,
    before_data JSON NULL,
    after_data JSON NULL,
    correlation_id CHAR(36) NULL,
    PRIMARY KEY (audit_id),
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES `user` (user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_audit_subject FOREIGN KEY (subject_user_id) REFERENCES `user` (user_id) ON DELETE RESTRICT,
    INDEX ix_audit_occurred_at (occurred_at),
    INDEX ix_audit_action_actor (action, actor_user_id, occurred_at),
    INDEX ix_audit_subject (subject_user_id, occurred_at),
    INDEX ix_audit_target (entity_type, entity_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TRIGGER trg_audit_log_no_update
BEFORE UPDATE ON audit_log FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUDIT_LOG is append-only';

CREATE TRIGGER trg_audit_log_no_delete
BEFORE DELETE ON audit_log FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AUDIT_LOG is append-only';
