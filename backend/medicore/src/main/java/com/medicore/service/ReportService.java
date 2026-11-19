package com.medicore.service;

import com.medicore.dto.ApiDtos;
import com.medicore.entity.ReportRecord;
import com.medicore.entity.ReportState;
import com.medicore.entity.RequestStatus;
import com.medicore.entity.TestRequest;
import com.medicore.entity.TestResult;
import com.medicore.entity.UserAccount;
import com.medicore.entity.UserRole;
import com.medicore.exception.DomainException;
import com.medicore.repository.ReportRecordRepository;
import com.medicore.repository.TestRequestRepository;
import com.medicore.repository.TestResultRepository;
import com.medicore.security.MediCorePrincipal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ReportService {
    private final ReportRecordRepository reports;
    private final TestRequestRepository requests;
    private final TestResultRepository results;
    private final WorkflowService workflow;
    private final AccountService accounts;
    private final AuditService audit;
    private final ReportFileStorage storage;
    private final PdfReportGenerator pdfGenerator;

    public ReportService(ReportRecordRepository reports, TestRequestRepository requests, TestResultRepository results,
                         WorkflowService workflow, AccountService accounts, AuditService audit,
                         ReportFileStorage storage, PdfReportGenerator pdfGenerator) {
        this.reports = reports;
        this.requests = requests;
        this.results = results;
        this.workflow = workflow;
        this.accounts = accounts;
        this.audit = audit;
        this.storage = storage;
        this.pdfGenerator = pdfGenerator;
    }

    @Transactional
    public ApiDtos.ReportView generate(Long technicianId, Long requestId) {
        UserAccount technician = accounts.requireRole(technicianId, UserRole.LAB_TECHNICIAN);
        TestRequest request = requests.findByIdForUpdate(requestId).orElseThrow(() -> DomainException.notFound("Test request not found."));
        workflow.requireAssignedRequest(technicianId, requestId);
        if (request.status != RequestStatus.COMPLETED && request.status != RequestStatus.REPORT_GENERATED) {
            throw DomainException.conflict("A report can be generated only after a result is completed.");
        }
        TestResult result = results.findFirstByRequest_IdOrderByVersionNoDesc(request.id)
                .orElseThrow(() -> DomainException.conflict("A result is required before report generation."));
        List<ReportRecord> history = reports.findAllByRequest_IdOrderByReportVersionDesc(request.id);
        ReportRecord latest = history.stream().max(Comparator.comparingInt(r -> r.reportVersion)).orElse(null);
        ReportRecord current = reports.findFirstByRequest_IdAndReportStateOrderByReportVersionDesc(request.id, ReportState.CURRENT).orElse(null);
        if (current != null && current.result.id.equals(result.id)) throw DomainException.conflict("A current report already exists for this result.");
        if (current != null) current.reportState = ReportState.SUPERSEDED;

        ReportRecord report = new ReportRecord();
        report.request = request;
        report.result = result;
        report.reportVersion = latest == null ? 1 : latest.reportVersion + 1;
        report.generatedBy = technician;
        report.reportDate = LocalDateTime.now(ZoneOffset.UTC);
        report.reportState = ReportState.CURRENT;
        report.supersedes = latest;
        String fileKey = UUID.randomUUID() + ".pdf";
        report.reportFileKey = fileKey;
        byte[] pdf = pdfGenerator.generate(request, result, report);
        storage.storeWithKey(fileKey, pdf);
        registerRollbackCleanup(fileKey);
        if (current != null) reports.save(current);
        reports.save(report);
        request.status = RequestStatus.REPORT_GENERATED;
        requests.save(request);
        audit.record(latest == null ? "REPORT_GENERATED" : "REPORT_REISSUED", technician, request.patient.user,
                "REPORT", report.id, latest == null ? null : Map.of("reportId", latest.id, "version", latest.reportVersion),
                Map.of("version", report.reportVersion, "resultId", result.id));
        if (latest == null) audit.record("TEST_STATUS_CHANGED", technician, request.patient.user, "TEST_REQUEST", request.id,
                Map.of("status", "COMPLETED"), Map.of("status", "REPORT_GENERATED"));
        return view(report);
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.ReportView> list(MediCorePrincipal principal) {
        List<ReportRecord> scoped;
        switch (principal.role()) {
            case PATIENT -> scoped = reports.findAllByRequest_Patient_User_IdOrderByReportDateDesc(principal.userId());
            case DOCTOR -> {
                List<Long> requestIds = requests.findAllByDoctor_User_IdOrderByRequestDateDesc(principal.userId()).stream()
                        .filter(r -> accounts.doctorCanAccess(principal.userId(), r.assignment)).map(r -> r.id).toList();
                scoped = requestIds.isEmpty() ? List.of() : reports.findAllByRequest_IdInOrderByReportDateDesc(requestIds);
            }
            case LAB_TECHNICIAN -> {
                List<Long> requestIds = requests.findAllByAssignedTechnician_IdOrderByScheduledDateAscScheduledTimeAsc(principal.userId())
                        .stream().map(r -> r.id).toList();
                scoped = requestIds.isEmpty() ? List.of() : reports.findAllByRequest_IdInOrderByReportDateDesc(requestIds);
            }
            case ADMIN -> scoped = reports.findAll().stream().sorted(Comparator.comparing((ReportRecord r) -> r.reportDate).reversed()).toList();
            default -> scoped = List.of();
        }
        return scoped.stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public ApiDtos.ReportView get(MediCorePrincipal principal, Long reportId) {
        return view(requireAccessibleReport(principal, reportId));
    }

    @Transactional(readOnly = true)
    public Download download(MediCorePrincipal principal, Long reportId) {
        ReportRecord report = requireAccessibleReport(principal, reportId);
        return new Download("report-" + report.id + ".pdf", storage.load(report.reportFileKey));
    }

    private ReportRecord requireAccessibleReport(MediCorePrincipal principal, Long reportId) {
        ReportRecord report = reports.findById(reportId).orElseThrow(() -> DomainException.notFound("Report not found."));
        workflow.requireAccessibleRequest(principal, report.request.id);
        return report;
    }

    private ApiDtos.ReportView view(ReportRecord report) {
        return new ApiDtos.ReportView(report.id, report.request.id, report.result.id, report.reportVersion,
                report.reportDate, report.reportState.name(), report.supersedes == null ? null : report.supersedes.id);
    }

    private void registerRollbackCleanup(String fileKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) storage.delete(fileKey);
            }
        });
    }

    public record Download(String filename, byte[] bytes) {}
}
