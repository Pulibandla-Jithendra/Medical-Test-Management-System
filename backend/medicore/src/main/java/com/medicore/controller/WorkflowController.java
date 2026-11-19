package com.medicore.controller;

import com.medicore.dto.ApiDtos;
import com.medicore.entity.RequestStatus;
import com.medicore.security.MediCorePrincipal;
import com.medicore.service.WorkflowService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class WorkflowController {
    private final WorkflowService workflow;

    public WorkflowController(WorkflowService workflow) { this.workflow = workflow; }

    @GetMapping("/medical-tests")
    public List<ApiDtos.MedicalTestView> medicalTests(@AuthenticationPrincipal MediCorePrincipal principal) {
        return workflow.medicalTests(principal.role().name().equals("ADMIN"));
    }

    @GetMapping("/medical-tests/{testId}")
    public ApiDtos.MedicalTestView medicalTest(@AuthenticationPrincipal MediCorePrincipal principal,
                                               @PathVariable Long testId) {
        ApiDtos.MedicalTestView test = workflow.medicalTest(testId);
        if (!test.active() && principal.role().name().equals("ADMIN")) return test;
        if (!test.active()) throw com.medicore.exception.DomainException.notFound("Medical test not found.");
        return test;
    }

    @PostMapping("/medical-tests")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.MedicalTestView createMedicalTest(@AuthenticationPrincipal MediCorePrincipal principal,
                                                    @Valid @RequestBody ApiDtos.MedicalTestRequest request) {
        return workflow.createMedicalTest(principal.userId(), request);
    }

    @PatchMapping("/medical-tests/{testId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiDtos.MedicalTestView updateMedicalTest(@AuthenticationPrincipal MediCorePrincipal principal,
                                                     @PathVariable Long testId,
                                                     @Valid @RequestBody ApiDtos.MedicalTestRequest request) {
        return workflow.updateMedicalTest(principal.userId(), testId, request);
    }

    @PostMapping("/test-requests")
    @PreAuthorize("hasRole('DOCTOR')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.RequestView createRequest(@AuthenticationPrincipal MediCorePrincipal principal,
                                             @Valid @RequestBody ApiDtos.TestRequestCreate request) {
        return workflow.createRequest(principal.userId(), request);
    }

    @GetMapping("/test-requests")
    public ApiDtos.RequestPage listRequests(@AuthenticationPrincipal MediCorePrincipal principal,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(required = false) RequestStatus status,
                                            @RequestParam(required = false) Long patientId,
                                            @RequestParam(required = false) Long testId,
                                            @RequestParam(required = false) LocalDate from,
                                            @RequestParam(required = false) LocalDate to,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "25") int size) {
        return workflow.listRequests(principal, q, status, patientId, testId, from, to, page, size);
    }

    @GetMapping("/test-requests/{requestId}")
    public ApiDtos.RequestView getRequest(@AuthenticationPrincipal MediCorePrincipal principal,
                                          @PathVariable Long requestId) {
        return workflow.getRequest(principal, requestId);
    }

    @GetMapping("/admin/test-requests")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiDtos.RequestPage adminRequests(@AuthenticationPrincipal MediCorePrincipal principal,
                                             @RequestParam(required = false) String q,
                                             @RequestParam(required = false) RequestStatus status,
                                             @RequestParam(required = false) Long patientId,
                                             @RequestParam(required = false) Long testId,
                                             @RequestParam(required = false) LocalDate from,
                                             @RequestParam(required = false) LocalDate to,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "25") int size) {
        return workflow.listRequests(principal, q, status, patientId, testId, from, to, page, size);
    }

    @GetMapping("/lab/work-queue")
    @PreAuthorize("hasRole('LAB_TECHNICIAN')")
    public ApiDtos.RequestPage workQueue(@AuthenticationPrincipal MediCorePrincipal principal,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(required = false) RequestStatus status,
                                         @RequestParam(required = false) Long patientId,
                                         @RequestParam(required = false) Long testId,
                                         @RequestParam(required = false) LocalDate from,
                                         @RequestParam(required = false) LocalDate to,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "25") int size) {
        return workflow.listRequests(principal, q, status, patientId, testId, from, to, page, size);
    }

    @PostMapping("/admin/test-requests/{requestId}/schedule")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiDtos.RequestView schedule(@AuthenticationPrincipal MediCorePrincipal principal,
                                        @PathVariable Long requestId,
                                        @Valid @RequestBody ApiDtos.ScheduleRequest request) {
        return workflow.schedule(principal.userId(), requestId, request);
    }

    @PostMapping("/admin/test-requests/{requestId}/reschedule")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiDtos.RequestView reschedule(@AuthenticationPrincipal MediCorePrincipal principal,
                                          @PathVariable Long requestId,
                                          @Valid @RequestBody ApiDtos.RescheduleRequest request) {
        return workflow.reschedule(principal.userId(), requestId, request);
    }

    @PostMapping("/test-requests/{requestId}/start")
    @PreAuthorize("hasRole('LAB_TECHNICIAN')")
    public ApiDtos.RequestView start(@AuthenticationPrincipal MediCorePrincipal principal,
                                     @PathVariable Long requestId) {
        return workflow.start(principal.userId(), requestId);
    }

    @PostMapping("/test-requests/{requestId}/results")
    @PreAuthorize("hasRole('LAB_TECHNICIAN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.ResultView enterResult(@AuthenticationPrincipal MediCorePrincipal principal,
                                          @PathVariable Long requestId,
                                          @Valid @RequestBody ApiDtos.ResultRequest request) {
        return workflow.enterInitialResult(principal.userId(), requestId, request);
    }

    @GetMapping("/test-requests/{requestId}/results")
    public List<ApiDtos.ResultView> results(@AuthenticationPrincipal MediCorePrincipal principal,
                                            @PathVariable Long requestId) {
        return workflow.results(principal, requestId);
    }

    @PostMapping("/test-requests/{requestId}/correction-requests")
    @PreAuthorize("hasRole('LAB_TECHNICIAN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.CorrectionView requestCorrection(@AuthenticationPrincipal MediCorePrincipal principal,
                                                    @PathVariable Long requestId,
                                                    @Valid @RequestBody ApiDtos.CorrectionRequest request) {
        return workflow.requestCorrection(principal.userId(), requestId, request);
    }

    @GetMapping("/admin/result-correction-requests")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ApiDtos.CorrectionView> correctionRequests() { return workflow.correctionRequests(); }

    @GetMapping("/lab/result-correction-requests")
    @PreAuthorize("hasRole('LAB_TECHNICIAN')")
    public List<ApiDtos.CorrectionView> technicianCorrections(@AuthenticationPrincipal MediCorePrincipal principal) {
        return workflow.technicianCorrections(principal.userId());
    }

    @PostMapping("/admin/result-correction-requests/{correctionId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiDtos.CorrectionView approve(@AuthenticationPrincipal MediCorePrincipal principal,
                                          @PathVariable Long correctionId,
                                          @RequestBody ApiDtos.CorrectionDecision request) {
        return workflow.decideCorrection(principal.userId(), correctionId, request, true);
    }

    @PostMapping("/admin/result-correction-requests/{correctionId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiDtos.CorrectionView reject(@AuthenticationPrincipal MediCorePrincipal principal,
                                         @PathVariable Long correctionId,
                                         @RequestBody ApiDtos.CorrectionDecision request) {
        return workflow.decideCorrection(principal.userId(), correctionId, request, false);
    }

    @PostMapping("/result-correction-requests/{correctionId}/apply")
    @PreAuthorize("hasRole('LAB_TECHNICIAN')")
    public ApiDtos.ResultView applyCorrection(@AuthenticationPrincipal MediCorePrincipal principal,
                                              @PathVariable Long correctionId,
                                              @Valid @RequestBody ApiDtos.ResultRequest request) {
        return workflow.applyCorrection(principal.userId(), correctionId, request);
    }

    @GetMapping("/admin/audit-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ApiDtos.AuditView> auditLogs() { return workflow.auditLogs(); }
}
