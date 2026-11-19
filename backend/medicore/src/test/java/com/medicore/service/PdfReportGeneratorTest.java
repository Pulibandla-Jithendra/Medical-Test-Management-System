package com.medicore.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.medicore.entity.DoctorProfile;
import com.medicore.entity.MedicalTest;
import com.medicore.entity.PatientProfile;
import com.medicore.entity.ReportRecord;
import com.medicore.entity.TestRequest;
import com.medicore.entity.TestResult;
import com.medicore.entity.UserAccount;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class PdfReportGeneratorTest {
    @Test
    void createsReadablePdfWithSyntheticRequestAndResultDetails() throws Exception {
        UserAccount patientUser = user(1L, "Casey Sample");
        UserAccount doctorUser = user(2L, "Morgan Demo");
        PatientProfile patient = new PatientProfile();
        patient.id = 101L;
        patient.user = patientUser;
        DoctorProfile doctor = new DoctorProfile();
        doctor.id = 202L;
        doctor.user = doctorUser;
        MedicalTest test = new MedicalTest();
        test.id = 303L;
        test.name = "Synthetic Blood Panel";
        test.referenceRange = "Demo reference range";
        TestRequest request = new TestRequest();
        request.id = 404L;
        request.patient = patient;
        request.doctor = doctor;
        request.medicalTest = test;
        request.requestDate = LocalDateTime.of(2026, 11, 20, 8, 30);
        TestResult result = new TestResult();
        result.id = 505L;
        result.request = request;
        result.resultDate = LocalDate.of(2026, 11, 20);
        result.resultValue = "Synthetic value: within demo range";
        result.remarks = "Synthetic demo only";
        ReportRecord report = new ReportRecord();
        report.reportVersion = 1;
        report.reportDate = LocalDateTime.of(2026, 11, 20, 9, 0);

        byte[] pdf = new PdfReportGenerator().generate(request, result, report);

        assertThat(pdf).startsWith("%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        try (var document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("MEDICORE - MEDICAL TEST REPORT", "Casey Sample", "Synthetic Blood Panel",
                    "Synthetic value: within demo range", "Synthetic demo only", "Report version: 1");
        }
    }

    private UserAccount user(Long id, String name) {
        UserAccount user = new UserAccount();
        user.id = id;
        user.name = name;
        return user;
    }
}
