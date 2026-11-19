package com.medicore.dto;

import com.medicore.entity.RequestStatus;
import com.medicore.entity.UserRole;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public final class ApiDtos {
    private ApiDtos() {}

    public record RegisterRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 10, max = 72) String password,
            @NotNull @Min(1) @Max(120) Integer age,
            @Size(max = 64) String gender,
            @Size(max = 32) String phone,
            @Size(max = 255) String address) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank @Size(max = 72) String password) {}
    public record UserView(Long id, Long patientId, Long doctorId, String name, String email, UserRole role, boolean enabled, Integer age,
                           String gender, String phone, String address, String specialization) {}
    public record ProfileUpdateRequest(@NotBlank @Size(max = 120) String name, @NotNull @Min(1) @Max(120) Integer age,
                                       @Size(max = 64) String gender, @Size(max = 32) String phone,
                                       @Size(max = 255) String address) {}
    public record DoctorProfileUpdateRequest(@NotBlank @Size(max = 120) String name,
                                            @NotBlank @Size(max = 160) String specialization) {}
    public record CreateUserRequest(@NotBlank @Size(max = 120) String name,
                                    @NotBlank @Email @Size(max = 254) String email,
                                    @NotBlank @Size(min = 10, max = 72) String password,
                                    @NotNull UserRole role,
                                    @Size(max = 160) String specialization,
                                    @Min(1) @Max(120) Integer age,
                                    @Size(max = 64) String gender,
                                    @Size(max = 32) String phone,
                                    @Size(max = 255) String address) {}
    public record UpdateUserRequest(UserRole role, Boolean enabled, @Size(max = 160) String specialization,
                                    @Min(1) @Max(120) Integer age, @Size(max = 64) String gender,
                                    @Size(max = 32) String phone, @Size(max = 255) String address) {}

    public record MedicalTestRequest(@NotBlank @Size(max = 160) String name, @Size(max = 4000) String description,
                                     @NotNull @DecimalMin("0.00") BigDecimal price,
                                     @Size(max = 255) String referenceRange, Boolean active) {}
    public record MedicalTestView(Long id, String name, String description, BigDecimal price,
                                  String referenceRange, boolean active) {}

    public record AssignmentRequest(@NotNull Long doctorId, @NotNull Long patientId) {}
    public record ConsentRequest(@NotBlank @Size(max = 32) String consentTextVersion) {}
    public record AssignmentView(Long id, Long doctorId, String doctorName, Long patientId,
                                 LocalDateTime assignedAt, LocalDateTime revokedAt, boolean consentActive) {}

    public record TestRequestCreate(@NotNull Long patientId, @NotNull Long testId) {}
    public record ScheduleRequest(@NotNull LocalDate scheduledDate, @NotNull LocalTime scheduledTime,
                                  @NotNull Long technicianId) {}
    public record RescheduleRequest(@NotNull LocalDate scheduledDate, @NotNull LocalTime scheduledTime,
                                    @NotNull Long technicianId) {}
    public record RequestView(Long id, Long patientId, String patientName, Long doctorId, String doctorName,
                              Long testId, String testName, Long technicianId, String technicianName,
                              LocalDateTime requestDate, LocalDate scheduledDate, LocalTime scheduledTime,
                              RequestStatus status) {}
    public record RequestPage(java.util.List<RequestView> content, int page, int size, long totalElements) {}

    public record ResultRequest(@NotBlank @Size(max = 10000) String resultValue, @Size(max = 10000) String remarks,
                                @NotNull LocalDate resultDate) {}
    public record ResultView(Long id, Long requestId, int versionNo, Long supersedesResultId, String resultValue,
                             String remarks, LocalDate resultDate, Long enteredByUserId, String enteredByName,
                             LocalDateTime enteredAt, String correctionReason) {}
    public record CorrectionRequest(@NotNull Long targetResultId, Long targetReportId,
                                    @NotBlank @Size(max = 1000) String reason) {}
    public record CorrectionDecision(@Size(max = 1000) String note) {}
    public record CorrectionView(Long id, Long requestId, Long targetResultId, Long targetReportId,
                                 String reason, LocalDateTime requestedAt, String status, String decisionNote,
                                 Long reviewedById, LocalDateTime reviewedAt, Long appliedResultId) {}
    public record ReportView(Long id, Long requestId, Long resultId, int reportVersion,
                             LocalDateTime reportDate, String state, Long supersedesReportId) {}
    public record AuditView(Long id, Long actorUserId, String action, String entityType, String entityId,
                            LocalDateTime occurredAt, String beforeData, String afterData, String correlationId) {}
}
