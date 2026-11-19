package com.medicore.service;

import com.medicore.dto.ApiDtos;
import com.medicore.entity.CorrectionStatus;
import com.medicore.entity.DoctorPatientAssignment;
import com.medicore.entity.DoctorProfile;
import com.medicore.entity.MedicalTest;
import com.medicore.entity.PatientProfile;
import com.medicore.entity.ReportRecord;
import com.medicore.entity.ReportState;
import com.medicore.entity.RequestStatus;
import com.medicore.entity.ResultCorrectionRequest;
import com.medicore.entity.TestRequest;
import com.medicore.entity.TestResult;
import com.medicore.entity.UserAccount;
import com.medicore.entity.UserRole;
import com.medicore.exception.DomainException;
import com.medicore.repository.DoctorPatientAssignmentRepository;
import com.medicore.repository.AuditLogRepository;
import com.medicore.repository.DoctorProfileRepository;
import com.medicore.repository.MedicalTestRepository;
import com.medicore.repository.PatientProfileRepository;
import com.medicore.repository.ReportRecordRepository;
import com.medicore.repository.ResultCorrectionRequestRepository;
import com.medicore.repository.TestRequestRepository;
import com.medicore.repository.TestResultRepository;
import com.medicore.repository.UserAccountRepository;
import com.medicore.security.MediCorePrincipal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowService {
    private final MedicalTestRepository tests;
    private final TestRequestRepository requests;
    private final TestResultRepository results;
    private final ResultCorrectionRequestRepository corrections;
    private final ReportRecordRepository reports;
    private final UserAccountRepository users;
    private final PatientProfileRepository patients;
    private final DoctorProfileRepository doctors;
    private final DoctorPatientAssignmentRepository assignments;
    private final AuditLogRepository auditLogs;
    private final AccountService accounts;
    private final AuditService audit;

    public WorkflowService(MedicalTestRepository tests, TestRequestRepository requests,
                           TestResultRepository results, ResultCorrectionRequestRepository corrections,
                           ReportRecordRepository reports, UserAccountRepository users,
                           PatientProfileRepository patients, DoctorProfileRepository doctors,
                           DoctorPatientAssignmentRepository assignments, AuditLogRepository auditLogs,
                           AccountService accounts, AuditService audit) {
        this.tests = tests;
        this.requests = requests;
        this.results = results;
        this.corrections = corrections;
        this.reports = reports;
        this.users = users;
        this.patients = patients;
        this.doctors = doctors;
        this.assignments = assignments;
        this.auditLogs = auditLogs;
        this.accounts = accounts;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.MedicalTestView> medicalTests(boolean includeInactive) {
        return (includeInactive ? tests.findAll() : tests.findAllByActiveTrueOrderByNameAsc())
                .stream().map(this::testView).toList();
    }

    @Transactional(readOnly = true)
    public ApiDtos.MedicalTestView medicalTest(Long testId) {
        return tests.findById(testId).map(this::testView)
                .orElseThrow(() -> DomainException.notFound("Medical test not found."));
    }

    @Transactional
    public ApiDtos.MedicalTestView createMedicalTest(Long actorId, ApiDtos.MedicalTestRequest input) {
        UserAccount actor = accounts.requireRole(actorId, UserRole.ADMIN);
        MedicalTest test = new MedicalTest();
        test.name = input.name().trim();
        test.description = AuthService.normalized(input.description());
        test.price = input.price();
        test.referenceRange = AuthService.normalized(input.referenceRange());
        test.active = input.active() == null || input.active();
        tests.save(test);
        audit.record("MEDICAL_TEST_CREATED", actor, actor, "MEDICAL_TEST", test.id, null, Map.of("active", test.active));
        return testView(test);
    }

    @Transactional
    public ApiDtos.MedicalTestView updateMedicalTest(Long actorId, Long testId, ApiDtos.MedicalTestRequest input) {
        UserAccount actor = accounts.requireRole(actorId, UserRole.ADMIN);
        MedicalTest test = tests.findById(testId).orElseThrow(() -> DomainException.notFound("Medical test not found."));
        Map<String, Object> before = Map.of("name", test.name, "active", test.active);
        test.name = input.name().trim();
        test.description = AuthService.normalized(input.description());
        test.price = input.price();
        test.referenceRange = AuthService.normalized(input.referenceRange());
        if (input.active() != null) test.active = input.active();
        tests.save(test);
        audit.record("MEDICAL_TEST_UPDATED", actor, actor, "MEDICAL_TEST", test.id, before,
                Map.of("name", test.name, "active", test.active));
        return testView(test);
    }

    @Transactional
    public ApiDtos.RequestView createRequest(Long doctorUserId, ApiDtos.TestRequestCreate input) {
        UserAccount doctorUser = accounts.requireRole(doctorUserId, UserRole.DOCTOR);
        DoctorProfile doctor = doctors.findByUser_Id(doctorUserId).orElseThrow(() -> DomainException.notFound("Doctor profile not found."));
        PatientProfile patient = patients.findById(input.patientId()).orElseThrow(() -> DomainException.notFound("Patient not found."));
        if (!patient.user.enabled || patient.user.role != UserRole.PATIENT) throw DomainException.invalid("The patient account is not enabled.");
        MedicalTest test = tests.findById(input.testId()).filter(t -> t.active)
                .orElseThrow(() -> DomainException.notFound("Active medical test not found."));
        DoctorPatientAssignment assignment = assignments
                .findFirstByDoctor_IdAndPatient_IdAndRevokedAtIsNullOrderByAssignedAtDesc(doctor.id, patient.id)
                .orElseThrow(() -> DomainException.forbidden("An active doctor-patient assignment is required."));
        if (!accounts.doctorCanAccess(doctorUserId, assignment)) {
            throw DomainException.forbidden("Active patient consent is required before creating a request.");
        }
        TestRequest request = new TestRequest();
        request.patient = patient;
        request.doctor = doctor;
        request.assignment = assignment;
        request.medicalTest = test;
        request.requestDate = now();
        request.status = RequestStatus.REQUESTED;
        requests.save(request);
        audit.record("TEST_REQUEST_CREATED", doctorUser, patient.user, "TEST_REQUEST", request.id, null,
                Map.of("status", request.status.name(), "testId", test.id));
        return requestView(request);
    }

    @Transactional(readOnly = true)
    public ApiDtos.RequestPage listRequests(MediCorePrincipal principal, String query, RequestStatus status, Long patientId,
                                            Long testId, LocalDate from, LocalDate to, int page, int size) {
        List<TestRequest> scope = switch (principal.role()) {
            case PATIENT -> requests.findAllByPatient_User_IdOrderByRequestDateDesc(principal.userId());
            case DOCTOR -> requests.findAllByDoctor_User_IdOrderByRequestDateDesc(principal.userId()).stream()
                    .filter(r -> accounts.doctorCanAccess(principal.userId(), r.assignment)).toList();
            case LAB_TECHNICIAN -> requests.findAllByAssignedTechnician_IdOrderByScheduledDateAscScheduledTimeAsc(principal.userId());
            case ADMIN -> requests.findAllByOrderByRequestDateDesc();
        };
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim().toLowerCase(java.util.Locale.ROOT);
        List<ApiDtos.RequestView> filtered = scope.stream()
                .filter(r -> normalizedQuery == null || matchesQuery(r, normalizedQuery))
                .filter(r -> status == null || r.status == status)
                .filter(r -> patientId == null || r.patient.id.equals(patientId))
                .filter(r -> testId == null || r.medicalTest.id.equals(testId))
                .filter(r -> from == null || !r.requestDate.toLocalDate().isBefore(from))
                .filter(r -> to == null || !r.requestDate.toLocalDate().isAfter(to))
                .map(this::requestView).toList();
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        int start = (int) Math.min((long) safePage * safeSize, filtered.size());
        int end = Math.min(start + safeSize, filtered.size());
        return new ApiDtos.RequestPage(filtered.subList(start, end), safePage, safeSize, filtered.size());
    }

    @Transactional(readOnly = true)
    public ApiDtos.RequestView getRequest(MediCorePrincipal principal, Long requestId) {
        return requestView(requireAccessibleRequest(principal, requestId));
    }

    @Transactional
    public ApiDtos.RequestView schedule(Long actorId, Long requestId, ApiDtos.ScheduleRequest input) {
        UserAccount actor = accounts.requireRole(actorId, UserRole.ADMIN);
        TestRequest request = requests.findByIdForUpdate(requestId).orElseThrow(() -> DomainException.notFound("Test request not found."));
        if (request.status != RequestStatus.REQUESTED) throw DomainException.conflict("Only a requested test can be scheduled.");
        UserAccount technician = requireTechnician(input.technicianId());
        validateSchedule(input.scheduledDate(), input.scheduledTime());
        request.scheduledDate = input.scheduledDate();
        request.scheduledTime = input.scheduledTime();
        request.assignedTechnician = technician;
        request.status = RequestStatus.SCHEDULED;
        requests.save(request);
        audit.record("TEST_SCHEDULED", actor, request.patient.user, "TEST_REQUEST", request.id, null,
                Map.of("date", request.scheduledDate.toString(), "time", request.scheduledTime.toString(), "technicianId", technician.id));
        audit.record("TEST_STATUS_CHANGED", actor, request.patient.user, "TEST_REQUEST", request.id,
                Map.of("status", "REQUESTED"), Map.of("status", "SCHEDULED"));
        return requestView(request);
    }

    @Transactional
    public ApiDtos.RequestView reschedule(Long actorId, Long requestId, ApiDtos.RescheduleRequest input) {
        UserAccount actor = accounts.requireRole(actorId, UserRole.ADMIN);
        TestRequest request = requests.findByIdForUpdate(requestId).orElseThrow(() -> DomainException.notFound("Test request not found."));
        if (request.status != RequestStatus.SCHEDULED) throw DomainException.conflict("Rescheduling is allowed only before processing starts.");
        validateSchedule(input.scheduledDate(), input.scheduledTime());
        UserAccount technician = requireTechnician(input.technicianId());
        Map<String, Object> before = Map.of("date", request.scheduledDate.toString(), "time", request.scheduledTime.toString(),
                "technicianId", request.assignedTechnician.id);
        request.scheduledDate = input.scheduledDate();
        request.scheduledTime = input.scheduledTime();
        request.assignedTechnician = technician;
        requests.save(request);
        Map<String, Object> after = Map.of("date", request.scheduledDate.toString(), "time", request.scheduledTime.toString(),
                "technicianId", technician.id);
        audit.record("TEST_RESCHEDULED", actor, request.patient.user, "TEST_REQUEST", request.id, before, after);
        return requestView(request);
    }

    @Transactional
    public ApiDtos.RequestView start(Long technicianId, Long requestId) {
        UserAccount technician = accounts.requireRole(technicianId, UserRole.LAB_TECHNICIAN);
        TestRequest request = requests.findByIdForUpdate(requestId).orElseThrow(() -> DomainException.notFound("Test request not found."));
        requireAssignedTechnician(request, technicianId);
        if (request.status != RequestStatus.SCHEDULED) throw DomainException.conflict("Only a scheduled request can be started.");
        request.status = RequestStatus.IN_PROGRESS;
        requests.save(request);
        audit.record("TEST_STATUS_CHANGED", technician, request.patient.user, "TEST_REQUEST", request.id,
                Map.of("status", "SCHEDULED"), Map.of("status", "IN_PROGRESS"));
        return requestView(request);
    }

    @Transactional
    public ApiDtos.ResultView enterInitialResult(Long technicianId, Long requestId, ApiDtos.ResultRequest input) {
        UserAccount technician = accounts.requireRole(technicianId, UserRole.LAB_TECHNICIAN);
        TestRequest request = requests.findByIdForUpdate(requestId).orElseThrow(() -> DomainException.notFound("Test request not found."));
        requireAssignedTechnician(request, technicianId);
        if (request.status != RequestStatus.IN_PROGRESS) throw DomainException.conflict("Results can be entered only while processing is in progress.");
        TestResult result = new TestResult();
        result.request = request;
        result.versionNo = 1;
        result.resultValue = input.resultValue().trim();
        result.remarks = AuthService.normalized(input.remarks());
        result.resultDate = input.resultDate();
        result.enteredBy = technician;
        result.enteredAt = now();
        results.save(result);
        request.status = RequestStatus.COMPLETED;
        requests.save(request);
        audit.record("RESULT_ENTERED", technician, request.patient.user, "TEST_RESULT", result.id,
                null, Map.of("requestId", request.id, "version", result.versionNo, "resultDate", result.resultDate.toString()));
        audit.record("TEST_STATUS_CHANGED", technician, request.patient.user, "TEST_REQUEST", request.id,
                Map.of("status", "IN_PROGRESS"), Map.of("status", "COMPLETED"));
        return resultView(result);
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.ResultView> results(MediCorePrincipal principal, Long requestId) {
        TestRequest request = requireAccessibleRequest(principal, requestId);
        return results.findAllByRequest_IdOrderByVersionNoAsc(request.id).stream().map(this::resultView).toList();
    }

    @Transactional
    public ApiDtos.CorrectionView requestCorrection(Long technicianId, Long requestId, ApiDtos.CorrectionRequest input) {
        UserAccount technician = accounts.requireRole(technicianId, UserRole.LAB_TECHNICIAN);
        TestRequest request = requests.findByIdForUpdate(requestId).orElseThrow(() -> DomainException.notFound("Test request not found."));
        requireAssignedTechnician(request, technicianId);
        if (request.status != RequestStatus.COMPLETED && request.status != RequestStatus.REPORT_GENERATED) {
            throw DomainException.conflict("Only a completed result can be corrected.");
        }
        TestResult current = results.findFirstByRequest_IdOrderByVersionNoDesc(requestId)
                .orElseThrow(() -> DomainException.conflict("The request has no result to correct."));
        if (!current.id.equals(input.targetResultId())) throw DomainException.conflict("The correction must target the current result version.");
        if (corrections.existsByRequest_IdAndTargetResult_IdAndStatus(requestId, current.id, CorrectionStatus.PENDING)) {
            throw DomainException.conflict("A pending correction already exists for the current result.");
        }
        ReportRecord currentReport = reports.findFirstByRequest_IdAndReportStateOrderByReportVersionDesc(requestId, ReportState.CURRENT).orElse(null);
        if (currentReport != null && (input.targetReportId() == null || !currentReport.id.equals(input.targetReportId()))) {
            throw DomainException.conflict("The current report version must be identified in the correction request.");
        }
        if (currentReport == null && input.targetReportId() != null) {
            throw DomainException.conflict("This request has no current report to correct.");
        }
        ResultCorrectionRequest correction = new ResultCorrectionRequest();
        correction.request = request;
        correction.targetResult = current;
        correction.targetReport = currentReport;
        correction.requestedBy = technician;
        correction.reason = input.reason().trim();
        correction.requestedAt = now();
        correction.status = CorrectionStatus.PENDING;
        corrections.save(correction);
        audit.record("RESULT_CORRECTION_REQUESTED", technician, request.patient.user, "RESULT_CORRECTION_REQUEST", correction.id,
                null, Map.of("requestId", request.id, "targetResultId", current.id, "reason", correction.reason));
        return correctionView(correction);
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.CorrectionView> correctionRequests() {
        return corrections.findAll().stream().sorted((a, b) -> b.requestedAt.compareTo(a.requestedAt)).map(this::correctionView).toList();
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.CorrectionView> technicianCorrections(Long technicianId) {
        accounts.requireRole(technicianId, UserRole.LAB_TECHNICIAN);
        return corrections.findAllByRequestedBy_IdOrderByRequestedAtDesc(technicianId).stream()
                .filter(c -> c.request.assignedTechnician != null && c.request.assignedTechnician.id.equals(technicianId))
                .map(this::correctionView).toList();
    }

    @Transactional
    public ApiDtos.CorrectionView decideCorrection(Long adminId, Long correctionId, ApiDtos.CorrectionDecision input,
                                                   boolean approve) {
        UserAccount admin = accounts.requireRole(adminId, UserRole.ADMIN);
        ResultCorrectionRequest correction = corrections.findByIdForUpdate(correctionId)
                .orElseThrow(() -> DomainException.notFound("Correction request not found."));
        if (correction.status != CorrectionStatus.PENDING) throw DomainException.conflict("This correction request has already been decided.");
        correction.status = approve ? CorrectionStatus.APPROVED : CorrectionStatus.REJECTED;
        correction.reviewedBy = admin;
        correction.reviewedAt = now();
        correction.decisionNote = AuthService.normalized(input.note());
        corrections.save(correction);
        audit.record(approve ? "RESULT_CORRECTION_APPROVED" : "RESULT_CORRECTION_REJECTED", admin,
                correction.request.patient.user, "RESULT_CORRECTION_REQUEST", correction.id, null,
                Map.of("targetResultId", correction.targetResult.id, "reason", correction.reason,
                        "approverId", admin.id, "reviewedAt", correction.reviewedAt.toString(), "status", correction.status.name()));
        return correctionView(correction);
    }

    @Transactional
    public ApiDtos.ResultView applyCorrection(Long technicianId, Long correctionId, ApiDtos.ResultRequest input) {
        UserAccount technician = accounts.requireRole(technicianId, UserRole.LAB_TECHNICIAN);
        ResultCorrectionRequest correction = corrections.findById(correctionId)
                .orElseThrow(() -> DomainException.notFound("Correction request not found."));
        TestRequest request = requests.findByIdForUpdate(correction.request.id)
                .orElseThrow(() -> DomainException.notFound("Test request not found."));
        requireAssignedTechnician(request, technicianId);
        if (correction.status != CorrectionStatus.APPROVED) throw DomainException.conflict("Administrator approval is required before applying this correction.");
        TestResult current = results.findFirstByRequest_IdOrderByVersionNoDesc(request.id)
                .orElseThrow(() -> DomainException.conflict("The request has no result to correct."));
        if (!current.id.equals(correction.targetResult.id)) throw DomainException.conflict("The result changed after this correction was requested.");

        TestResult replacement = new TestResult();
        replacement.request = request;
        replacement.versionNo = current.versionNo + 1;
        replacement.supersedes = current;
        replacement.resultValue = input.resultValue().trim();
        replacement.remarks = AuthService.normalized(input.remarks());
        replacement.resultDate = input.resultDate();
        replacement.enteredBy = technician;
        replacement.enteredAt = now();
        replacement.correctionReason = correction.reason;
        results.save(replacement);
        correction.appliedResult = replacement;
        correction.status = CorrectionStatus.APPLIED;
        corrections.save(correction);
        if (correction.targetReport != null) correction.targetReport.reportState = ReportState.SUPERSEDED;
        if (correction.targetReport != null) reports.save(correction.targetReport);
        audit.record("RESULT_CORRECTED", technician, request.patient.user, "TEST_RESULT", replacement.id,
                Map.of("priorResultId", current.id, "version", current.versionNo),
                Map.of("supersedesResultId", current.id, "version", replacement.versionNo,
                        "correctionRequestId", correction.id, "approverId", correction.reviewedBy.id,
                        "reason", correction.reason, "enteredAt", replacement.enteredAt.toString()));
        if (correction.targetReport != null) {
            audit.record("REPORT_SUPERSEDED", technician, request.patient.user, "REPORT", correction.targetReport.id,
                    Map.of("state", "CURRENT"), Map.of("state", "SUPERSEDED", "replacementResultId", replacement.id));
        }
        return resultView(replacement);
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.AuditView> auditLogs() {
        return auditLogs.findTop500ByOrderByOccurredAtDesc().stream()
                .map(a -> new ApiDtos.AuditView(a.id, a.actor == null ? null : a.actor.id, a.action, a.entityType,
                        a.entityId, a.occurredAt, a.beforeData, a.afterData, a.correlationId)).toList();
    }

    @Transactional(readOnly = true)
    public TestRequest requireAccessibleRequest(MediCorePrincipal principal, Long requestId) {
        TestRequest request = requests.findById(requestId).orElseThrow(() -> DomainException.notFound("Test request not found."));
        boolean permitted = switch (principal.role()) {
            case ADMIN -> true;
            case PATIENT -> request.patient.user.id.equals(principal.userId());
            case DOCTOR -> accounts.doctorCanAccess(principal.userId(), request.assignment);
            case LAB_TECHNICIAN -> request.assignedTechnician != null && request.assignedTechnician.id.equals(principal.userId());
        };
        if (!permitted) throw DomainException.forbidden("You are not authorized to access this test request.");
        return request;
    }

    @Transactional(readOnly = true)
    public TestRequest requireAssignedRequest(Long technicianId, Long requestId) {
        TestRequest request = requests.findById(requestId).orElseThrow(() -> DomainException.notFound("Test request not found."));
        requireAssignedTechnician(request, technicianId);
        return request;
    }

    private void requireAssignedTechnician(TestRequest request, Long technicianId) {
        if (request.assignedTechnician == null || !request.assignedTechnician.id.equals(technicianId)) {
            throw DomainException.forbidden("This test request is not assigned to your account.");
        }
    }

    private boolean matchesQuery(TestRequest request, String query) {
        return request.id.toString().contains(query)
                || String.format(java.util.Locale.ROOT, "TR-%04d", request.id).toLowerCase(java.util.Locale.ROOT).contains(query)
                || request.patient.user.name.toLowerCase(java.util.Locale.ROOT).contains(query)
                || request.patient.user.email.toLowerCase(java.util.Locale.ROOT).contains(query)
                || request.doctor.user.name.toLowerCase(java.util.Locale.ROOT).contains(query)
                || request.medicalTest.name.toLowerCase(java.util.Locale.ROOT).contains(query)
                || request.status.name().toLowerCase(java.util.Locale.ROOT).contains(query)
                || (request.assignedTechnician != null && request.assignedTechnician.name.toLowerCase(java.util.Locale.ROOT).contains(query));
    }

    private UserAccount requireTechnician(Long id) {
        UserAccount user = users.findById(id).orElseThrow(() -> DomainException.notFound("Technician not found."));
        if (!user.enabled || user.role != UserRole.LAB_TECHNICIAN) throw DomainException.invalid("Select an enabled laboratory technician.");
        return user;
    }

    private void validateSchedule(LocalDate date, LocalTime time) {
        if (date.isBefore(LocalDate.now(ZoneOffset.UTC)) || (date.equals(LocalDate.now(ZoneOffset.UTC)) && time.isBefore(LocalTime.now(ZoneOffset.UTC)))) {
            throw DomainException.invalid("The schedule must be in the future.");
        }
    }

    private ApiDtos.MedicalTestView testView(MedicalTest t) {
        return new ApiDtos.MedicalTestView(t.id, t.name, t.description, t.price, t.referenceRange, t.active);
    }

    private ApiDtos.RequestView requestView(TestRequest r) {
        return new ApiDtos.RequestView(r.id, r.patient.id, r.patient.user.name, r.doctor.id, r.doctor.user.name,
                r.medicalTest.id, r.medicalTest.name, r.assignedTechnician == null ? null : r.assignedTechnician.id,
                r.assignedTechnician == null ? null : r.assignedTechnician.name,
                r.requestDate, r.scheduledDate, r.scheduledTime, r.status);
    }

    private ApiDtos.ResultView resultView(TestResult r) {
        return new ApiDtos.ResultView(r.id, r.request.id, r.versionNo, r.supersedes == null ? null : r.supersedes.id,
                r.resultValue, r.remarks, r.resultDate, r.enteredBy.id, r.enteredBy.name, r.enteredAt, r.correctionReason);
    }

    private ApiDtos.CorrectionView correctionView(ResultCorrectionRequest c) {
        return new ApiDtos.CorrectionView(c.id, c.request.id, c.targetResult.id,
                c.targetReport == null ? null : c.targetReport.id, c.reason, c.requestedAt, c.status.name(),
                c.decisionNote, c.reviewedBy == null ? null : c.reviewedBy.id, c.reviewedAt,
                c.appliedResult == null ? null : c.appliedResult.id);
    }

    private LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }

}
