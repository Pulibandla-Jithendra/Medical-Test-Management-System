package com.medicore.service;

import com.medicore.entity.ReportRecord;
import com.medicore.entity.TestRequest;
import com.medicore.entity.TestResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

@Service
public class PdfReportGenerator {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'");

    public byte[] generate(TestRequest request, TestResult result, ReportRecord report) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            List<String> lines = new ArrayList<>();
            lines.add("MEDICORE - MEDICAL TEST REPORT");
            lines.add("Report version: " + report.reportVersion);
            lines.add("Patient: " + request.patient.user.name + " (Patient ID " + request.patient.id + ")");
            lines.add("Doctor: " + request.doctor.user.name);
            lines.add("Test: " + request.medicalTest.name);
            lines.add("Request date: " + DATE_TIME.format(request.requestDate));
            lines.add("Result date: " + DATE.format(result.resultDate));
            lines.add("Result: " + result.resultValue);
            lines.add("Remarks: " + (result.remarks == null ? "None" : result.remarks));
            lines.add("Reference range: " + (request.medicalTest.referenceRange == null ? "Not specified" : request.medicalTest.referenceRange));
            lines.add("Report date: " + DATE_TIME.format(report.reportDate));
            float y = 770;
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                for (int i = 0; i < lines.size(); i++) {
                    content.beginText();
                    content.setFont(i == 0 ? bold : regular, i == 0 ? 16 : 11);
                    content.newLineAtOffset(52, y);
                    content.showText(asciiSafe(lines.get(i)));
                    content.endText();
                    y -= i == 0 ? 38 : 27;
                }
                content.beginText();
                content.setFont(regular, 9);
                content.newLineAtOffset(52, 70);
                content.showText("Academic prototype report. Results require interpretation by an authorized clinician.");
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("The PDF report could not be generated.");
        }
    }

    private String asciiSafe(String value) {
        String normalized = value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ");
        StringBuilder safe = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            safe.append(c >= 32 && c <= 126 ? c : '?');
        }
        return safe.toString();
    }
}
