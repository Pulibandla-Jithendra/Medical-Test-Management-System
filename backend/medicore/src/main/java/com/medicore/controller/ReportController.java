package com.medicore.controller;

import com.medicore.dto.ApiDtos;
import com.medicore.security.MediCorePrincipal;
import com.medicore.service.ReportService;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ReportController {
    private final ReportService reports;

    public ReportController(ReportService reports) { this.reports = reports; }

    @PostMapping("/test-requests/{requestId}/reports")
    @PreAuthorize("hasRole('LAB_TECHNICIAN')")
    public ApiDtos.ReportView generate(@AuthenticationPrincipal MediCorePrincipal principal,
                                       @PathVariable Long requestId) {
        return reports.generate(principal.userId(), requestId);
    }

    @GetMapping("/reports")
    public List<ApiDtos.ReportView> list(@AuthenticationPrincipal MediCorePrincipal principal) {
        return reports.list(principal);
    }

    @GetMapping("/reports/{reportId}")
    public ApiDtos.ReportView get(@AuthenticationPrincipal MediCorePrincipal principal,
                                  @PathVariable Long reportId) {
        return reports.get(principal, reportId);
    }

    @GetMapping("/reports/{reportId}/file")
    public ResponseEntity<byte[]> file(@AuthenticationPrincipal MediCorePrincipal principal,
                                       @PathVariable Long reportId) {
        ReportService.Download download = reports.download(principal, reportId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(download.filename()).build().toString())
                .body(download.bytes());
    }
}
